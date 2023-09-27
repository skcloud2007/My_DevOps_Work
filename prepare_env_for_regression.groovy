currentBuild.description="preparing VM ${target_vm_ip} for regression"
pipeline{
    options{
      timeout(time: 10 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    agent{
        node {
            label "${jenkins_node}"
        }
    }
    environment{
       TMP_DIR="/tmp/${BUILD_TAG}"
    }
    stages{
        stage ('load common groovy'){
            steps {
                script{
                    notify_slack = load "${WORKSPACE}/jenkins_pipeline/common/notify_on_slack.groovy"
                    channel_list_slack = load "${WORKSPACE}/jenkins_pipeline/common/slack_channel_list.groovy"
                    common_func= load "${WORKSPACE}/jenkins_pipeline/common/utils.groovy" 

                }
            }
        }
        stage("validate parameter"){
            steps {
                script{
                    if (!"${target_vm_ip}"){
                        echo "Provide vm ip"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"
                    }
                    if (!"${version}"){
                        echo "Provide version"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"
                    }
                }
            }
        }
        stage("side load test data and open neo4port"){
            when{
                beforeAgent true
                expression { return params.LOAD_TEST_DATA }
            }
            steps{
                script{
                    sideload_jenkins_node=""
                    // if("${target_vm_ip}".startsWith("10.106.")){
                    //     sideload_jenkins_node="solution-maker-ap-south-1"
                    // }else if("${target_vm_ip}".startsWith("10.78.") || "${target_vm_ip}".startsWith("10.75.")){
                    //     sideload_jenkins_node="solution-maker"
                    // }
                    versionsArr="${version}".split('\\.')
                    majorVersion=versionsArr[0]
                    minorVersion=versionsArr[1]
                    incrementVersion=""
                    qualifier=""
                    // if("${version}".indexOf('-')==-1){
                    //     incrementVersion=versionsArr[2]
                    // }else{
                    //     incrementVersion=versionsArr[2].split('\\-')[0]
                    //     qualifier="-"+versionsArr[2].split('\\-')[1]
                    // }
                    echo "open neo4j port"
                    build(job: 'custartifacts-automation',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'product_line', value: "${product_line}"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_increment', value: "0"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_qualifier', value: "-SNAPSHOT"],
                        [$class: 'StringParameterValue',
                        name: 'input_target_vm_ip', value: "${target_vm_ip}"],
                        [$class: 'StringParameterValue',
                        name: 'input_sideloading_group', value: "neo4jport"],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_branch', value: "master"]
                    ])
                    echo "wait for green nagios after port open"
                    sleep 120
                    sh "python3 jenkins_pipeline/common/nagios_check.py --bpHost $target_vm_ip --retries 500"
                    sideLoadingGroup=""
                    if("${product_line}"=="bpinventory"){
                        sideLoadingGroup="bpitestdata"
                    }else if("${product_line}"=="fusioncore"){
                        sideLoadingGroup="fcshowcaseall"
                    }
                    build(job: 'custartifacts-automation',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'product_line', value: "${product_line}"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_major', value: majorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_minor', value: minorVersion],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_increment', value: "0"],
                        [$class: 'StringParameterValue',
                        name: 'release_ver_qualifier', value: "-SNAPSHOT"],
                        [$class: 'StringParameterValue',
                        name: 'input_target_vm_ip', value: "${target_vm_ip}"],
                        [$class: 'StringParameterValue',
                        name: 'input_sideloading_group', value: sideLoadingGroup],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_branch', value: "master"]
                    ])
                    echo "wait for green nagios after sideload test data"
                    sleep 120
                    sh "python3 jenkins_pipeline/common/nagios_check.py --bpHost $target_vm_ip --retries 500"
                }
            }            
        }
        stage("Create kafka topics"){
            when{
                beforeAgent true
                expression { return params.CREATE_KAFKA_TOPICS }
            }
            steps{
                sh '''
                    cd $WORKSPACE/library/shell_scripts/
                    chmod 755 create_kafka_topics.sh
                    /bin/bash create_kafka_topics.sh $target_vm_ip
                '''
            }
        }
    }
    post{
        always{
            script{
                echo currentBuild.result
               // notify_slack.general_slack_notification("${SLACK_CHANNEL}",currentBuild.result)
            }
        }
        cleanup {
            deleteDir()
        }
    }

}
