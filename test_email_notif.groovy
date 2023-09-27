pipeline{
    agent any
    stages{
        stage ("load common groovy"){
            steps {
                script{
                    notify_slack = load "jenkins_pipeline/common/notify_on_slack.groovy"
                    channel_list_slack = load "jenkins_pipeline/common/slack_channel_list.groovy"
                    common_func= load "jenkins_pipeline/common/utils.groovy" 
                    email= load "jenkins_pipeline/common/send_email.groovy" 
                }
            }

        }
        stage("run shell"){
            steps{
                script{
                    echo "testing email"
                }
            }

        }
    }
    post{
        always{
            script{
                email.send_email_general()
            }
            
        }
    }
}