
import jenkins.model.*
import hudson.model.*
import groovy.json.JsonOutput
import java.net.HttpURLConnection
import java.net.URL
import groovy.transform.Field



//  * Jenkins Telegram Notification Script
//  * 
//  * This Groovy script is designed to run within Jenkins to monitor build statuses
//  * and send notifications to a specified list of Telegram chat IDs when a job's
//  * latest build fails. The script performs the following actions:
//  * 
//  * 1. Retrieves all jobs configured in Jenkins.
//  * 2. Checks the latest build result of each job.
//  * 3. If the latest build has failed, it adds the job to a list of failed builds.
//  * 4. Sends a Telegram message with details about the failed build.
//  * 5. The Telegram notification includes job name, build number, status, and a
//  *    clickable link to the failed build in Jenkins.
//  * 
//  * Configuration:
//  * - Set the TELEGRAM_BOT_TOKEN variable with your bot's API token.
//  * - Add relevant Telegram chat IDs to the TELEGRAM_CHAT_IDS list.
//  * - Update JENKINS_URL with your Jenkins instance URL.



// Telegram Bot Configuration
@Field TELEGRAM_BOT_TOKEN = ""
@Field TELEGRAM_CHAT_IDS = ["843837982", "218531327", "806405978"]
@Field JENKINS_URL = "http://192.168.3.3:8080/"

def sendTelegramMessage(String message) {
    TELEGRAM_CHAT_IDS.each { chatId ->
        def encodedMessage = URLEncoder.encode(message, "UTF-8")
        def urlString = "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage?chat_id=${chatId}&text=${encodedMessage}&parse_mode=Markdown"
        
        try {
            def url = new URL(urlString)
            def connection = (HttpURLConnection) url.openConnection()
            connection.setRequestMethod("GET")
            connection.connect()
            
            def responseCode = connection.getResponseCode()
            if (responseCode != 200) {
                println "Failed to send message to Telegram. Response Code: ${responseCode}"
            }
        } catch (Exception e) {
            println "Error sending message: ${e.message}"
        }
    }
}

// Track only the latest failed builds
def failedBuilds = []
def allJobs = Jenkins.instance.getAllItems(Job)

allJobs.each { job ->
    def lastBuild = job.getLastBuild()
    if (lastBuild?.result == Result.FAILURE) {
        failedBuilds << [jobName: job.name, buildNumber: lastBuild.number]
    }
}

if (!failedBuilds.isEmpty()) {
    failedBuilds.each { failedBuild ->
        def buildUrl = "${JENKINS_URL}job/${failedBuild.jobName}/${failedBuild.buildNumber}/"
        def escapedBuildUrl = buildUrl.replaceAll(/([)\\[\\]{}*_`])/, '\\\\$1')

        def message = """
        🚀 *Jenkins Build Notification* 🚀
        
        🔹 *Job*: ${failedBuild.jobName}  
        🔹 *Build Number*: #${failedBuild.buildNumber}  
        🔹 *Status*: ❌ Failed  
        🔹 *Build URL*: [Click Here](${escapedBuildUrl})  
        """.stripIndent()
        
        sendTelegramMessage(message)
    }
}
