currentBuild.description="${target_vm_ip}-${bpi_version}"
def majorVersion
def minorVersion
def incrementVersion
def qualifier
def vmIP

pipeline {
    options{
      timeout(time: 20 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    environment {
        TEMP_DIR="/tmp/${BUILD_TAG}"
        SLACK_CHANNEL_NAME='ankit-test-slack-notifications'
        SOLUTION_AUTOMATION_JOB_RESULT='' //placeholder for installation-automation-onlinemode job, stage "Install on a AWS VM"
        AWS_REGION="us-east-1"
        AWS_SUBNET="subnet-0481e5d25c8df1918"      
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
                    common_func= load "jenkins_pipeline/common/utils.groovy" 
                }
            }
        }
        stage ("validate parameters"){
            steps{
                script{
                    if(!"${bpi_version}"){
                        echo "Please provide required parameters"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"                        
                    }
                }
            }
        }
        stage("calculate version"){
            steps{
                script{
                    if("${bpi_version}".contains("-")){
                            versn_without_qualifier="${bpi_version}".split('\\-')[0]
                            qualifier="-"+"${bpi_version}".split('\\-')[1]                          
                    }else{
                            versn_without_qualifier="${bpi_version}"
                            qualifier=""
                    }
                    versionArr=versn_without_qualifier.split('\\.')
                    majorVersion=versionArr[0]
                    minorVersion=versionArr[1]
                    incrementVersion=versionArr[2]
                    echo "major veriosn is "+majorVersion+" minor version is "+minorVersion+" increment version is "+incrementVersion+" qualifier is "+qualifier
                }
            }

        }
        stage("Create solution"){
            when{
                beforeAgent false
                expression{
                    return params.build_solution
                }
            }
            steps{
                script{
                    build(job: 'solution-automation',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'product_line', value: "bpinventory"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_increment', value: incrementVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_qualifier', value: qualifier],
                        [$class: 'StringParameterValue',
                        name: 'installer_archetype_version', value: "${installer_archetype_version}"],
                        [$class: 'BooleanParameterValue',
                        name: 'allow_open_neo4j_port', value: true],
                        [$class: 'StringParameterValue',
                        name: 'var_lineup_xp_solverbase', value: ""],
                        [$class: 'StringParameterValue',
                        name: 'var_lineup_fc_solverbase', value: "${var_lineup_fc_solverbase}"],
                        [$class: 'StringParameterValue',
                        name: 'testing_prefix', value: "${testing_prefix}"],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_branch', value: "${dr_infra_version}"],
                        [$class: 'BooleanParameterValue',
                        name: 'download_archetype', value: true ],
                        [$class: 'BooleanParameterValue',
                        name: 'build_flexnet', value: false],
                        [$class: 'StringParameterValue',
                        name: 'lib_sol_chart_ver', value: ""],
                        [$class: 'BooleanParameterValue',
                        name: 'publish_bp2', value: true],
                        [$class: 'BooleanParameterValue',
                        name: 'publish_helm', value: true]
                    ])

                }
            }

        }
        stage("Install on a AWS VM"){
            steps{
                script{
                    autovm_comments="${BUILD_TAG}"
                    isNewVmRequired=false
                    if(!"${target_vm_ip}"){
                        isNewVmRequired=true                      
                    }
                    SOLUTION_AUTOMATION_JOB_RESULT=build(job: 'installation-automation-onlinemode',
                            propagate: true, parameters: [[$class: 'StringParameterValue',
                            name: 'env_owner', value: "Artem"],
                            [$class: 'StringParameterValue',
                            name: 'product_line', value: "bpinventory"],
                            [$class: 'StringParameterValue',
                            name: 'autovm_comments', value: autovm_comments],
                            [$class: 'BooleanParameterValue',
                            name: 'cicd_create_new_vm', value: isNewVmRequired],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_major', value: majorVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_minor', value: minorVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_increment', value: incrementVersion],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_qualifier', value: qualifier],
                            [$class: 'StringParameterValue',
                            name: 'var_lineup_xp_solverbase', value: "${var_lineup_xp_solverbase}"],
                            [$class: 'StringParameterValue',
                            name: 'var_lineup_fc_solverbase', value: "${var_lineup_fc_solverbase}"],
                            [$class: 'StringParameterValue',
                            name: 'target_vm_ip', value: "${target_vm_ip}"],
                            [$class: 'StringParameterValue',
                            name: 'testing_prefix', value: "${testing_prefix}"],
                            [$class: 'StringParameterValue',
                            name: 'aws_vm_region', value: "${AWS_REGION}"],
                            [$class: 'StringParameterValue',
                            name: 'target_vm_subnet', value: "${AWS_SUBNET}"],
                            [$class: 'StringParameterValue',
                            name: 'dr_infra_branch', value: "${dr_infra_version}"]
                    ])

                }
            }

        }
        stage("determine ip to use"){
            steps{
                script{
                    if(!"${target_vm_ip}"){
                        ip = sh(script:"""
                            set -x
                            aws ec2 --region ${AWS_REGION} describe-instances --filters \"Name=tag:it_jenkins_build,Values=jenkins-installation-automation-onlinemode-\"${SOLUTION_AUTOMATION_JOB_RESULT.getId()}\"\" --query \"Reservations[*].Instances[*].{ip:PrivateIpAddress}\" --output json | jq -r '.[0][0].ip' """,returnStdout:true)  
                        vmIP = ip.replaceAll("\\s","")                    
                    }else{
                        vmIP = "${target_vm_ip}"           

                    }
                }

            }

        }
        stage("Nagios clear check"){
            steps{
                script{
                    sh "python3 jenkins_pipeline/common/nagios_check.py --bpHost $vmIP --retries 500"

                }
                
            }

        }
        stage("Prepare env for regression"){
            steps{
                script{
                    build(job: 'Prepare_env_for_regression',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'target_vm_ip', value: vmIP],
                        [$class: 'StringParameterValue',
                        name: 'version', value: "${bpi_version}" ],
                        [$class: 'StringParameterValue',
                        name: 'product_line', value: "bpinventory" ],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_version', value: "${dr_infra_version}"],
                        [$class: 'StringParameterValue',
                        name: 'jenkins_node', value: "${jenkins_node}"]
                    ])

                }
            }

        }
        stage("deploy bmn files in vm"){
            when{
                beforeAgent false
                expression{
                    return params.deploy_bpmn_files
                }
            }
            steps{
                script{
                    build(job: 'bpmn_files_deployment',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'target_vm_ip', value: vmIP],
                        [$class: 'StringParameterValue',
                        name: 'bpmn_git_version', value: "${bpmn_ui_branch}" ],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_version', value: "${dr_infra_version}"],
                        [$class: 'StringParameterValue',
                        name: 'jenkins_node', value: "${jenkins_node}"]
                    ])

                }
            }

        }
        stage("regression prepare DB"){
            steps{
               sh 'sleep 5m'
               script{
                    sh "python3 jenkins_pipeline/common/nagios_check.py --bpHost $vmIP --retries 500"
                    regressionJobName=getPrepareDBJobName(majorVersion,minorVersion)
                    build(job: regressionJobName,
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'target.env', value: vmIP],
                        [$class: 'StringParameterValue',
                        name: 'browser', value: "chrome" ],
                        [$class: 'StringParameterValue',
                        name: 'testScenario', value: "@prepareDB" ],
                        [$class: 'StringParameterValue',
                        name: 'profile', value: "e2e-jenkins-platform-appbar" ]
                    ])  
               }             
                
            }
        }
        stage("Run regression"){
            steps{
                script{
                    echo "nagios check after prepareDB"
                    sh "python3 jenkins_pipeline/common/nagios_check.py --bpHost $vmIP --retries 500"
                    regressionJobName=getRegressionJobName(majorVersion,minorVersion)
                    build(job: regressionJobName,
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'target.env', value: vmIP],
                        [$class: 'StringParameterValue',
                        name: 'browser', value: "chrome.parallel" ],
                        [$class: 'StringParameterValue',
                        name: 'testScenario', value: "@all" ],
                        [$class: 'StringParameterValue',
                        name: 'profile', value: "e2e-test-jenkins-binary" ]
                    ])  

                }
            }

        }
        stage("Repack solution"){
            when{
                beforeAgent false
                expression{
                    return params.build_solution
                }
            }
           steps{
              script{
                 build(job: 'util-repacksolution',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'product_line', value: "bpinventory"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_increment', value: incrementVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_qualifier', value: qualifier],
                        [$class: 'StringParameterValue',
                        name: 'prefix_toremove', value: "${testing_prefix}"]
                    ])
                 
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
        // success{
        // //if success delete launched VM
        //  script{
        //     instance_id = sh(script:"""
        //                         set -x
        //                         aws ec2 --region ${aws_region} describe-instances --filters \"Name=tag:it_jenkins_build,Values=jenkins-installation-automation-onlinemode-\"${SOLUTION_AUTOMATION_JOB_RESULT.getId()}\"\" --query \"Reservations[*].Instances[*].{id:InstanceId}\" --output json | jq -r '.[0][0].id' """,returnStdout:true)
        //                         if ("${instance_id}" ){
        //                             sh(script: "aws ec2 --region ${aws_region} terminate-instances --instance-ids ${instance_id}")
        //                         }
                          

        //     }
        // }
        cleanup {
            deleteDir()
        }
    }
 


}

def getRegressionJobName(majorVersion,minorVersion){
    if(majorVersion.toInteger()==22 && minorVersion.toInteger()==12){
        return "BPI autotests (all) 22.12 PLATFORM-APPBAR"
    }
    else if(majorVersion.toInteger()==23 && minorVersion.toInteger()==4){
        return "BPI-autotests-(all)-23.04-PLATFORM-APPBAR"
    }

}

def getPrepareDBJobName(majorVersion,minorVersion){
    if(majorVersion.toInteger()==22 && minorVersion.toInteger()==12){
        return "BPI autotests (all) 22.12 PLATFORM-APPBAR"
    }
    else if(majorVersion.toInteger()==23 && minorVersion.toInteger()==4){
        return "BPI autotests (by feature tags) 23.04 PLATFORM-APPBAR"
    }

}

