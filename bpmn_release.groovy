currentBuild.description="release for dev ${bpmn_ui_branch}"
def release_orchestrator_branch="${cicd_orchestrator_branch}"
def release_orchestrator_event="${cicd_orchestrator_event}"
def allEventName="all"
pipeline {
    options{
      timeout(time: 10 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    environment {
        TEMP_DIR="/tmp/${BUILD_TAG}"
        SLACK_CHANNEL_NAME='bpmn-devops'
        BPMN_CURRENT_VERSION=""
        COREUI_CURRENT_VERSION=""
        AWS_REGION="us-east-1"
        AWS_SUBNET="subnet-0481e5d25c8df1918"
        BPMN_TAG_NAME=""
        MAJOR_VERSION=""
        MINOR_VERSION=""
        INCREMENT_VERSION=""
        RELEASE_QUALIFIER=""
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
        stage ('calculate versions'){
            steps {
                script{
                    dir("$TEMP_DIR/"){
                    git url: 'https://bitbucket.ciena.com/scm/bp_camunda_bpmn/bp-bpmn-ui.git',
                        branch: '${bpmn_ui_branch}'

                    BPMN_CURRENT_VERSION = sh(script:"""
                            set -x
                            cat app-server/blueplanet-bpmn-ui-config/pom.xml | grep '<!--Keep for CI/CD: blueplanet.bpmn.version-->' | awk -F '<version>' '{print \$2}' | awk -F '</version>' '{print \$1}' | tr -d '\n' """,returnStdout:true)
                    COREUI_CURRENT_VERSION = sh(script:"""
                            set -x
                            cat app-server/blueplanet-bpmn-ui-config/pom.xml | grep '<!--Keep for CI/CD: blueplanet.core.ui.version-->' | awk -F '<version>' '{print \$2}' | awk -F '</version>' '{print \$1}' | tr -d '\n' """,returnStdout:true)
                                       
                    echo " bpmn current version is ${BPMN_CURRENT_VERSION} and core-ui current version is ${COREUI_CURRENT_VERSION}"          
                    versions="${BPMN_CURRENT_VERSION}".split('\\.')
                    MAJOR_VERSION=versions[0]
                    MINOR_VERSION=versions[1]
                    INCREMENT_VERSION=versions[2].split('\\-')[0]
                    if(!"${target_release_ver_qualifier}"){
                        BPMN_TAG_NAME="v"+"$MAJOR_VERSION"+"."+"$MINOR_VERSION"+"."+"$INCREMENT_VERSION"
                    }else{
                        BPMN_TAG_NAME="v"+"$MAJOR_VERSION"+"."+"$MINOR_VERSION"+"."+"$INCREMENT_VERSION"+"$target_release_ver_qualifier"
                    }
                    if(params.DEV_BUILD){
                        BPMN_TAG_NAME=params.bpmn_ui_branch
                        //releaseQualifier=currentQualifier
                    }                    
                    echo "major version is "+"$MAJOR_VERSION"+" minor version is "+"$MINOR_VERSION"+" increment version is "+"$INCREMENT_VERSION and tag name will be ${BPMN_TAG_NAME}"
                    }
                }                
            }
        }
        stage("Create bpmn ui tag"){
            when{
                beforeAgent true
                expression {
                    if( !params.DEV_BUILD && ( "${release_orchestrator_event}"== "bpmn_publish_sdk" ||  "${release_orchestrator_event}"== "${allEventName}")){
                        return true
                    }else{
                        return false
                    }
                }
            }
         steps{
             withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                sh """
                export ANSIBLE_FORCE_COLOR=true 
                ansible-playbook -vvv playbooks/cicd/bpmn_release.yml --vault-password-file $ANSIBLEVAULT \\
                -e bpmn_ui_branch=${bpmn_ui_branch} \\
                -e release_core_ui_version=${release_core_ui_version} \\
                -e target_release_ver_qualifier=${target_release_ver_qualifier} \\
                -e bpmn_ui_current_version=${BPMN_CURRENT_VERSION} \\
                -e core_ui_current_version=${COREUI_CURRENT_VERSION} \\
                -e bpmn_ui_tag_version=${BPMN_TAG_NAME} \\
                -e bpmn_create_tag=true 

                """
          }
          
            }
        }
        stage ("Build bpmn ui"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "bpmn_publish_sdk" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    job_name=''
                    build_npm_22_12_onward_flag=false
                    if( ("${MAJOR_VERSION}".toInteger()==21 && "${MINOR_VERSION}".toInteger()==10) || "${MAJOR_VERSION}".toInteger()>=22  ){
                        job_name='BPMN-UI-Build-Pipeline-22.02-lib-upgrade'
                    }else{
                        job_name='BPMN-UI-Build-Pipeline'
                    }
                    if ("${MAJOR_VERSION}".toInteger()>=23 || ("${MAJOR_VERSION}".toInteger()>=22 && "${MINOR_VERSION}".toInteger()>=12)){
                        build_npm_22_12_onward_flag=true
                    }
                    build job: job_name, 
                        parameters: [extendedChoice(name: 'components-to-build', value: 'bpmn-rest,bpmn-ui-npm-package,bpmn-ui-showcase,common-bpmn-postgres-core,common-bpmn-postgres-example'),
                        string(name: 'bpmn.git.branch', value: "${BPMN_TAG_NAME}"), 
                        booleanParam(name: 'build_npm_22_12_onward', value: "${build_npm_22_12_onward_flag}"),
                        booleanParam(name: 'release', value: params.DEV_BUILD ? false : true)]

                }
            }

         }
        stage ("create bpmn app and solution"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "bpmn_publish_test_sol" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    camunda_version=""
                    if(!"${bpmn_sol_version}"){
                        camunda_version="${BPMN_CURRENT_VERSION}".split('\\-')[0]
                    }else{
                        camunda_version="${bpmn_sol_version}"
                    }
                    chartLocation=""
                    if(!"${target_release_ver_qualifier}"){
                        chartLocation="helm-local-ga"
                        bpmn_ui_version="${BPMN_CURRENT_VERSION}".split('\\-')[0]
                    }else{
                        chartLocation="helm-local-dev"
                        bpmn_ui_version="${BPMN_CURRENT_VERSION}".split('\\-')[0]+"${target_release_ver_qualifier}"
                    }
                    build(job: 'solution-automation-bpmn',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'bpmn_release_version', value: camunda_version],
                        [$class: 'StringParameterValue',
                        name: 'camunda_branch', value: "${camunda_branch}"],
                        [$class: 'StringParameterValue',
                        name: 'socamunda_branch', value: "${socamunda_branch}"],
                        [$class: 'StringParameterValue',
                        name: 'dr_infra_version', value: "${dr_infra_version}"],
                        [$class: 'StringParameterValue',
                        name: 'chart_location', value: chartLocation],
                        [$class: 'BooleanParameterValue',
                        name: 'dry_run', value: false],
                        [$class: 'StringParameterValue',
                        name: 'test_prefix', value: "${testing_prefix}"],
                        [$class: 'StringParameterValue',
                        name: 'bpmn_ui_version', value: bpmn_ui_version ]
                    ])
                }
            }

        }
        stage ("install bpmn solution in a aws vm"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "bpmn_publish_regression_test" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    rel_version=""
                    if(!"${bpmn_sol_version}"){
                        rel_version="${BPMN_CURRENT_VERSION}".split('\\-')[0]
                    }else{
                        rel_version="${bpmn_sol_version}"
                    }
                    versions=rel_version.split('\\.')
                    major_version=versions[0]
                    minor_version=versions[1]
                    increment_version=versions[2]
                    autovm_comments="-BPMN-Release"
                    INSTALLATION_AUTOMATION_JOB_RESULT=build(job: 'installation-automation-onlinemode',
                            propagate: true, wait: true, parameters: [[$class: 'StringParameterValue',
                            name: 'env_owner', value: "sachinja,pkushwah"],
                            [$class: 'StringParameterValue',
                            name: 'product_line', value: "bpmn"],
                            [$class: 'StringParameterValue',
                            name: 'autovm_comments', value: autovm_comments ],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_major', value: major_version],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_minor', value: minor_version],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_increment', value: increment_version],
                            [$class: 'StringParameterValue',
                            name: 'release_ver_qualifier', value: ""],
                            [$class: 'StringParameterValue',
                            name: 'var_lineup_fc_solverbase', value: "${release_core_ui_version}"],
                            [$class: 'StringParameterValue',
                            name: 'var_lineup_bpmn_solverbase', value: ""],
                            [$class: 'StringParameterValue',
                            name: 'aws_vm_region', value: "$AWS_REGION"],
                            [$class: 'StringParameterValue',
                            name: 'target_vm_subnet', value: "$AWS_SUBNET"],
                            [$class: 'StringParameterValue',
                            name: 'BP2_solutions', value: "${BP2_solutions}"]
                    ])
                }
            }

        }
        stage("Run regression on launched VM"){
            when{
                beforeAgent true
                expression {
                    if("${release_orchestrator_event}"== "bpmn_publish_regression_test" ||  "${release_orchestrator_event}"== "${allEventName}"){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE'){
                        sh 'sleep 20m'
                        new_vm_ip = sh(script:"""
                                set -x
                                aws ec2 --region "${AWS_REGION}" describe-instances --filters \"Name=tag:it_jenkins_build,Values=jenkins-installation-automation-onlinemode-\"${INSTALLATION_AUTOMATION_JOB_RESULT.getId()}\"\" --query \"Reservations[*].Instances[*].{ip:PrivateIpAddress}\" --output json | jq -r '.[0][0].ip' """,returnStdout:true)
                        echo "ip of launched aws ec2 is "+new_vm_ip
                        jobName=getRegressionJobName("${MAJOR_VERSION}".toInteger(),"${MINOR_VERSION}".toInteger())
                        echo "triggering build on job "+jobName
                        build(job: jobName,
                                propagate: true,wait: true, parameters: [[$class: 'StringParameterValue',
                                name: 'target.env', value: "${new_vm_ip}"],
                                [$class: 'StringParameterValue',
                                name: 'browser', value: "chrome"],
                                [$class: 'StringParameterValue',
                                name: 'testScenario', value: "@workFlowManager" ],
                                [$class: 'StringParameterValue',
                                name: 'email_list', value: "pkushwah@ciena.com"],
                                [$class: 'StringParameterValue',
                                name: 'profile', value: "e2e-jenkins-platform-appbar"],
                                [$class: 'StringParameterValue',
                                name: 'bpmn.git.branch', value: "${BPMN_TAG_NAME}"],
                                [$class: 'StringParameterValue',
                                name: 'url', value: "blueplanet-app-bar-ui"]
                        ])
                    }
                    
                }
            }

        }
        stage("repack bpmn solution"){            
            when{
                beforeAgent true
                expression {
                    if("${testing_prefix}" && ("${release_orchestrator_event}"== "bpmn_publish_sol_sdk" ||  "${release_orchestrator_event}"== "${allEventName}")){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                script{
                    if((currentBuild.currentResult=='UNSTABLE' || currentBuild.currentResult=='SUCCESS')){
                        build(job: 'util-repacksolution',
                                propagate: true, parameters: [[$class: 'StringParameterValue',
                                name: 'product_line', value: "bpmn"],
                                [$class: 'StringParameterValue',
                                name: 'release_ver_major', value: "${MAJOR_VERSION}"],
                                [$class: 'StringParameterValue',
                                name: 'release_ver_minor', value: "${MINOR_VERSION}"],
                                [$class: 'StringParameterValue',
                                name: 'release_ver_increment', value: "${INCREMENT_VERSION}"],
                                [$class: 'StringParameterValue',
                                name: 'release_ver_qualifier', value: ""],
                                [$class: 'StringParameterValue',
                                name: 'prefix_toremove', value: "${testing_prefix}"]
                            ])
                    }else{
                        sh 'exit 1'
                    }
                }

            }            

        }
        stage("create PR for pom update in dev branches"){
            when{
                beforeAgent true
                expression {
                    if( !params.DEV_BUILD && ( !common_func.is_fc_rc_release("${target_release_ver_qualifier}") && ("${release_orchestrator_event}"== "bpmn_publish_sol_sdk" ||  "${release_orchestrator_event}"== "${allEventName}"))){
                        return true
                    }else{
                        return false
                    }
                }
            }
            steps{
                withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                sh """
                export ANSIBLE_FORCE_COLOR=true 
                ansible-playbook -vvv playbooks/cicd/bpmn_release.yml --vault-password-file $ANSIBLEVAULT \\
                -e bpmn_ui_branch=${bpmn_ui_branch} \\
                -e release_core_ui_version=${release_core_ui_version} \\
                -e target_release_ver_qualifier=${target_release_ver_qualifier} \\
                -e bpmn_ui_current_version=${BPMN_CURRENT_VERSION} \\
                -e core_ui_current_version=${COREUI_CURRENT_VERSION} \\
                -e camunda_branch=${camunda_branch} \\
                -e bpmn_ui_tag_version=${BPMN_TAG_NAME} \\
                -e bpmn_update_dev_version=true 
                """
          }
            }

        }
    }
    post {
        always{
            script{
                if (currentBuild.result == 'SUCCESS' || currentBuild.result == 'UNSTABLE'){
                    final_result = 'SUCCESS'
                }
                else{
                    final_result = currentBuild.result
                }
                echo final_result
                // echo currentBuild.result
                //notify_slack.general_slack_notification("${SLACK_CHANNEL_NAME}",currentBuild.result)
                BRANCH_NAME="${release_orchestrator_branch}"
                EVENT_NAME="${release_orchestrator_event}"
                VERSION_PUBLISHED="${MAJOR_VERSION}.${MINOR_VERSION}.${INCREMENT_VERSION}"
                RELEASE_VERSION="${MAJOR_VERSION}.${MINOR_VERSION}.${INCREMENT_VERSION}"
                CURRENT_BUILD=env.BUILD_ID
                if ("${release_orchestrator_branch}"){
                    sh(script:"""
                        set -x
                        bash jenkins_pipeline/common/push_events.sh $BRANCH_NAME $EVENT_NAME $VERSION_PUBLISHED $RELEASE_VERSION ${final_result} $CURRENT_BUILD

                    """)
                }
            }
        }
        cleanup {
            sh 'sudo rm -rf $TEMP_DIR'
            deleteDir()
        }
    }
}

def getRegressionJobName(major_version,minor_version){
    if(major_version==21 && minor_version==6 ){
        return "BPMN autotests (by feature tags and all) 21.06"
    }else if(major_version==21 && minor_version==10){
        return "BPMN autotests (by feature tags and all) 21.10"
    }else if(major_version==22 && minor_version==2){
        return "BPMN autotests (by feature tags and all) 22.02"
    }else if(major_version==22 && minor_version==8){
        return "BPMN autotests (by feature tags and all) 22.08"
    }else if(major_version==22 && minor_version==12){
        return "BPMN autotests (by feature tags and all) 22.12"
    }
    return ""

}

// def getBpmnTagName(bpmnCurrentVersion){
//     versions=bpmnCurrentVersion.split('\\.')
//     major_version=versions[0]
//     minor_version=versions[1]
//     increment_version=versions[2].split('\\-')[0]
//     bpmnTagName="v"+major_version+"."+minor_version+"."+increment_version
//     return bpmnTagName

// }

//@NonCPS
def getElementValue(xmlFilePath,xpath){
   // 
    echo("parser created")
    xmlContent=readFile(xmlFilePath)
    //echo(xmlContent)
    xmlParser = new XmlParser()
    node=xmlParser.parseText(xmlContent)
    //File file=new File(xmlFilePath)
    //xmlContent=xmlParser.parse(file)
    echo("file has been read successfully")
    //value=xmlContent.xpath
    //value=node.project.version
    return node.parent.version
}

