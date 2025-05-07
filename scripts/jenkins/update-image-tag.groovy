pipeline {
    agent any
    parameters {
        string(name: 'newImageTag', defaultValue: '', description: 'The new Docker image tag')
        choice(name: 'environment', choices: ['prod', 'dev', 'qa'], description: 'Environment to deploy (prod, dev, qa)')
        string(name: 'appName', defaultValue: '', description: 'Name of the application')
    }

    environment {
        BASE_DIR = './kuber/environments'
        BRANCH = determineBranch(params.environment)
        IMAGE_NAME = params.newImageTag.replace("http://", "").replace("https://", "")
    }

    stages {
        stage('Validate Parameters') {
            steps {
                script {
                    // Validate that parameters are not empty
                    if (!params.newImageTag || !params.environment || !params.appName) {
                        error 'All parameters (newImageTag, environment, appName) are required.'
                    }

                    // Construct manifest file path
                    env.MANIFEST_FILE = "${BASE_DIR}/${params.environment}/apps/${params.appName}-cf.yaml"

                    // Check if manifest file exists
                    if (!fileExists(env.MANIFEST_FILE)) {
                        error "Manifest file not found: ${env.MANIFEST_FILE}"
                    }
                }
            }
        }

        stage('scm') {
            steps {
                checkout scm
            }
        }
        stage('Checkout') {
            steps {
                sshagent(credentials: ['bitbucket-ssh-key']) {
                    script {
                        try {
                            sh """
                    git remote set-url origin git@bitbucket.org:project/devops.git

                    # Fetch remote changes
                    git fetch origin ${BRANCH}

                    # Rebase local changes on top of remote changes
                    git rebase origin/${BRANCH}

                    git checkout ${BRANCH} || git checkout -b ${BRANCH}

                    git reset --hard origin/${BRANCH}
                    """
                            echo "Successfully pushed changes to ${BRANCH}"
                } catch (Exception e) {
                            echo "Failed to push changes: ${e.getMessage()}"
                            error "Push failed: ${e.getMessage()}"
                        }
                    }
                }
                // Checkout the repository
                script {
                    sh '''
                    #!/bin/bash
                    set -e
                    git config --global user.name "Jenkins"
                    git config --global user.email "jenkins@example.com"
                    git config --global credential.helper store
                    '''
                }
            }
        }

        stage('Update Manifest') {
            steps {
                // Update the image in the manifest file
                script {
                    sh """
                    sed -i "s|image: .*|image: ${IMAGE_NAME}|" ${env.MANIFEST_FILE}
                    """
                    echo "Manifest updated: ${env.MANIFEST_FILE}"
                }
            }
        }

        stage('Commit') {
            steps {
                // Commit and push the updated manifest
                script {
                    // Ensure the changes are committed before switching branches
                    sh """
                    #!/bin/bash
                    set -e

                    # Check if there are changes to commit
                    git add ${env.MANIFEST_FILE}
                    git commit -m "Update image in ${params.appName} to ${IMAGE_NAME}"

                    # Stash changes if needed (optional)
                    git stash || echo "No changes to stash"

                    # Checkout the branch safely
                    git stash pop || echo "No stashed changes"
                    """
                    echo 'Manifest changes committed and pushed to the repository.'
                }
            }
        }

        stage('Push Changes') {
            steps {
                sshagent(credentials: ['bitbucket-ssh-key']) {
                    script {
                        try {
                            sh """
                    # Push the changes
                    git push origin ${BRANCH}
                    """
                            echo "Successfully pushed changes to ${BRANCH}"
                } catch (Exception e) {
                            echo "Failed to push changes: ${e.getMessage()}"
                            error "Push failed: ${e.getMessage()}"
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'Pipeline completed successfully!'
        }
        failure {
            echo 'Pipeline failed. Check logs for details.'
        }
    }
}

def determineBranch(environment) {
    return 'dev'
}
