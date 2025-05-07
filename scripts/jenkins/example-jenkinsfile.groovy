pipeline {
    agent any

    environment {
        APP_NAME = 'frontend_app'
        RELEASE = 'x.x.x' // Will be replaced with the actual release version during the pipeline
        BRANCH_NAME = "${env.GIT_BRANCH?.replaceAll('origin/', '')?:'unknown'}" // Extracts the branch name from the full path
        IMAGE_NAME = "${APP_NAME}-${BRANCH_NAME}" // Adds branch to the image name
        IMAGE_TAG = "${RELEASE}-${BUILD_NUMBER}" // Tags image with branch name, will be updated with the actual release version during the pipeline
        IMAGE_FULL_NAME = "${IMAGE_NAME}:${IMAGE_TAG}" // Full image name with tag, will be updated
        ENVIRONMENT = determineEnvironment(BRANCH_NAME) // Dynamically determine the environment
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Test coverage for sonarqube-analysis') {
            steps {
                script {
                    sh 'npm ci'
                    sh 'NODE_OPTIONS="--max_old_space_size=4096 --inspect" npm run test:coverage -- --no-cache --detectOpenHandles'
                }
            }
        }

        stage('SonarQube analysis') {
            steps {
                script {
                    scannerHome = tool 'sonarqube-scanner-latest'
                }
                withSonarQubeEnv('sonarqube-scanner-latest') {
                    sh "${scannerHome}/bin/sonar-scanner"
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Bump version') {
            when {
                expression { ENVIRONMENT == 'dev' }
            }
            steps {
                script {
                    withCredentials([string(credentialsId: 'BUMP_VERSION_SCRIPT_URL', variable: 'BUMP_VERSION_SCRIPT_URL')]) {
                        sshagent(credentials: ['bitbucket-ssh-key']) {
                            try {
                                sh """
                                git remote set-url origin git@bitbucket.org:project/${APP_NAME}.git

                                # Fetch remote changes
                                git fetch origin ${BRANCH_NAME}

                                # Rebase local changes on top of remote changes
                                git rebase origin/${BRANCH_NAME}

                                git checkout ${BRANCH_NAME} || git checkout -b ${BRANCH_NAME}

                                git reset --hard origin/${BRANCH_NAME}
                                """
                            } catch (Exception e) {
                                echo "Failed to rebase the branch: ${e.message}"
                                error 'Stopping the pipeline due to failure.'
                            }
                            sh "cp ${BUMP_VERSION_SCRIPT_URL} ./"
                            sh 'chmod +x bump-version.bash'
                            sh '''
                            #!/bin/bash
                            set -e
                            git config --global user.name "Jenkins"
                            git config --global user.email "jenkins@example.com"
                            git config --global credential.helper store
                            '''
                            sh 'bash ./bump-version.bash'
                        }
                    }
                }
            }
        }

        stage('Update envs') {
            steps {
                script {
                    RELEASE = sh(script: 'jq -r ".version" package.json', returnStdout: true).trim()
                    IMAGE_TAG = "${RELEASE}-${BUILD_NUMBER}"
                    IMAGE_FULL_NAME = "${IMAGE_NAME}:${IMAGE_TAG}"
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    withCredentials([string(credentialsId: 'DOCKER_REGISTRY_URL', variable: 'DOCKER_REGISTRY')]) {
                        docker.withRegistry("${DOCKER_REGISTRY}") {
                            docker_image = docker.build("${IMAGE_NAME}:${IMAGE_TAG}")
                            // Tag as latest as well
                            sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest"
                            // Verify images are present
                            sh "docker images | grep ${IMAGE_NAME}"
                        }
                    }
                }
            }
        }

        stage('Trivy Scan Docker Image') {
            steps {
                script {
                    // Scan the Docker image with Trivy
                    sh "trivy image --exit-code 1 --severity CRITICAL --no-progress ${IMAGE_NAME}:${IMAGE_TAG}"
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    withCredentials([string(credentialsId: 'DOCKER_REGISTRY_URL', variable: 'DOCKER_REGISTRY')]) {
                        docker.withRegistry("${DOCKER_REGISTRY}") {
                            docker_image.push("${IMAGE_TAG}")
                            docker_image.push('latest')
                        }
                    }
                }
            }
        }

        stage('Update K8s Manifest') {
            when {
                expression { ENVIRONMENT != 'unknown' }
            }

            steps {
                script {
                    withCredentials([string(credentialsId: 'DOCKER_REGISTRY_URL', variable: 'DOCKER_REGISTRY')]) {
                        try {
                            build job: 'image-tag-updater', parameters: [
                            string(name: 'newImageTag', value: "${DOCKER_REGISTRY}/${IMAGE_FULL_NAME}"),
                            string(name: 'environment', value: ENVIRONMENT),
                            string(name: 'appName', value: APP_NAME)
                            ]
                        } catch (Exception e) {
                                echo "Failed to trigger the pipeline: ${e.message}"
                                error 'Stopping the pipeline due to failure.'
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                echo "Success: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
            }
        }
        failure {
            script {
                echo "Failed: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})"
            }
        }
        cleanup {
            script {
                cleanupDockerImages("${IMAGE_NAME}", "${IMAGE_TAG}")
                cleanupDockerImages("${IMAGE_NAME}", 'latest')
            }
        }
    }
}

// Helper function to determine the environment based on the branch name
def determineEnvironment(branchName) {
    if (branchName == 'main') {
        return 'prod'
    } else if (branchName == 'dev') {
        return 'dev'
    } else if (branchName == 'qa') {
        return 'qa'
    } else {
        return 'unknown'
    }
}

def cleanupDockerImages(imageName, imageTag) {
    try {
        echo "Cleaning up Docker image: ${imageName}:${imageTag}"
        // Remove the specific image built during this pipeline
        sh "docker rmi ${imageName}:${imageTag} || true"
    } catch (e) {
        echo "Error during cleanup: Image ${imageName}:${imageTag} not found"
    }
}
