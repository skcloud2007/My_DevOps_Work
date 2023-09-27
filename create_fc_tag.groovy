

pipeline {
    options{
      timeout(time: 5 , unit: 'HOURS')
      ansiColor('gnome-terminal')

    }

    environment {
        TEMP_DIR='/tmp'
        SLACK_CHANNEL_NAME='ankit-notification-center'

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

        stage ("checkout DR-INFRA repo") {
            steps{
              
             git url: 'https://git.blueplanet.com/Ciena/BPI/infrastructure/dr-infra.git' ,
                 credentialsId: '6f94116a-b375-47dd-b050-739213759120' ,
                 branch: '${dr_infra_version}'
            }
        }
        stage("Create Tag and update POM files"){

           steps{
           withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                sh """
                export ANSIBLE_FORCE_COLOR=true 
                ansible-playbook -vvv playbooks/cicd/fc_release.yml --vault-password-file $ANSIBLEVAULT \\
                -e from_version=${fc_version_from} -e to_tag_version=${fc_tag_version_to} \\
                -e curr_release_ver_major=${curr_release_ver_major} -e curr_release_ver_minor=${curr_release_ver_minor} \\
                -e curr_release_ver_increment=${curr_release_ver_increment} -e curr_release_ver_qualifier=${curr_release_ver_qualifier} \\
                -e target_release_ver_major=${target_release_ver_major} -e target_release_ver_minor=${target_release_ver_minor} \\
                -e target_release_ver_increment=${target_release_ver_increment} -e target_release_ver_qualifier=${target_release_ver_qualifier} \\
                -e core_ui_create_tag=true

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
        cleanup {
            deleteDir()
        }
    }
 


}