import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
def AGENT_LABEL = null

node('master') {
  stage('Checkout and set agent'){
     
     if ("${input_target_vm_ip}".startsWith("10.106.")) {
        AGENT_LABEL = "solution-maker-ap-south-1-v1"
     }else if("${input_target_vm_ip}".startsWith("10.78.") || "${input_target_vm_ip}".startsWith("10.75.")){
        AGENT_LABEL = "solution-maker"
     }else{
        AGENT_LABEL = "solution-maker"
     }
   }
}
pipeline {
    options{
      timeout(time: 10 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    environment {
        SLACK_CHANNEL_NAME='bpi-installer-automation-notification'     
    }
    agent{
        node {
            label "${AGENT_LABEL}"
        }
    }
    stages{
        stage ('load common groovy'){
            steps {
                script{
                    
                    notify_slack = load "jenkins_pipeline/common/notify_on_slack.groovy"
                    channel_list_slack = load "jenkins_pipeline/common/slack_channel_list.groovy"
                    common_func= load "jenkins_pipeline/common/utils.groovy" 
                    email= load "jenkins_pipeline/common/send_email.groovy" 
                }
            }
        }
        stage ('sideloading'){
            steps{
                script{
                    stage ('bpinventory sideloading'){

                        if("${product_line}"=='bpinventory'){

                            ansiblePlaybook colorized: true, credentialsId: 'bpidev-bpi-devops-dr', extras: '-vvv -e \'{"release_ver_major":"${release_ver_major}","release_ver_minor":"${release_ver_minor}","release_ver_increment":"${release_ver_increment}","release_ver_qualifier":"${release_ver_qualifier}","product_line":"${product_line}","input_target_vm_ip":"${input_target_vm_ip}","flexnet_support":"${flexnet_support}","input_sideloading_group":"${input_sideloading_group}","environ":"${environ}"}\'', installation: 'ansible2.8.5', playbook: 'playbooks/cicd/pb-bpinstaller-4.3.1-loadbpicustartifact.yml', vaultCredentialsId: 'ansible-vaultkey'

                        }else{
                            Utils.markStageSkippedForConditional('bpinventory sideloading')
                        }
                    }
                    stage ('fusioncore sideloading'){

                        if("${product_line}"=='fusioncore'){

                            ansiblePlaybook colorized: true, credentialsId: 'bpidev-bpi-devops-dr', extras: '-vvv -e \'{"release_ver_major":"${release_ver_major}","release_ver_minor":"${release_ver_minor}","release_ver_increment":"${release_ver_increment}","release_ver_qualifier":"${release_ver_qualifier}","product_line":"${product_line}","input_target_vm_ip":"${input_target_vm_ip}","flexnet_support":"${flexnet_support}","input_sideloading_group":"${input_sideloading_group}","environ":"${environ}"}\'', installation: 'ansible2.8.5', playbook: 'playbooks/cicd/pb-bpinstaller-4.3.2-loadfccustartifact.yml', vaultCredentialsId: 'ansible-vaultkey'

                        }else{
                            Utils.markStageSkippedForConditional('fusioncore sideloading')
                        }
                    }
                }
            }
        }
    }
    post{
        failure{
            script{
                echo currentBuild.result
                notify_slack.general_slack_notification("${SLACK_CHANNEL_NAME}",currentBuild.result)
                email.send_email_general()
            }
        }
        cleanup {
            deleteDir()
        }
    }
}
