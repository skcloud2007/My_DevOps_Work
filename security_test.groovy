currentBuild.description = "Security test on ${target_vm_ip}"

pipeline {
    options{
      timeout(time: 5 , unit: 'HOURS')
      ansiColor('gnome-terminal')

    }

    environment {
        TEMP_DIR='/tmp'
        SLACK_CHANNEL_NAME='ankit-test-slack-notifications'
        scan_report_file="SensitiveDataMatched_${BUILD_NUMBER}.tar.gz"
        scan_report_dir="reports"

    }

    agent{
        node {
            label "${jenkins_node}"
        }
    }

    stages{
        stage ('load common groovy'){
            steps {
                script{
                    notify_slack = load "jenkins_pipeline/common/notify_on_slack.groovy"
                    channel_list_slack = load "jenkins_pipeline/common/slack_channel_list.groovy"
                }
            }

        }

        // stage ("checkout DR-INFRA repo") {
        //     steps{
              
        //      git url: 'https://git.blueplanet.com/Ciena/BPI/infrastructure/dr-infra.git' ,
        //          credentialsId: '6f94116a-b375-47dd-b050-739213759120' ,
        //          branch: '${dr_infra_version}'
        //     }
        // }
        stage("Sensitive data check in logs"){

           steps{
           withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                sh """
                export ANSIBLE_FORCE_COLOR=true
                ansible-playbook playbooks/testing/security_test.yml --vault-password-file $ANSIBLEVAULT \\
                -e bpi_git_version=${bpi_git_version} -e target_vm_ip=${target_vm_ip} \\
                -e scan_report_dir=${scan_report_dir} -e jenkins_workspace=${WORKSPACE}
                """
           }
            

            }

        }
    }
    post {
        always{
            script{
                echo currentBuild.result
                notify_slack.general_slack_notification("${SLACK_CHANNEL_NAME}",currentBuild.result)
            }
        }
        success{
          sh 'tar -zcvf ${scan_report_file} ${scan_report_dir}'          
          archiveArtifacts artifacts: '*.gz'    
        }
        cleanup {
            deleteDir()
        }
    }
 


}