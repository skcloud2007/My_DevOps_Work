currentBuild.description="FNR=${FNR_BRANCH},MM=${MM_BRANCH},GM=${GM_BRANCH},msi=${BPI_MS_ARCHETYPE_BRANCH},release=${RELEASE_MS}"
def fnrCurrentVersion
def mmCurrentVersion
def gmCurrentVersion
def archetypeCurrentVersion
def fnrComponentName="fnr"
def fnrRepo="https://bitbucket.ciena.com/scm/bp_inventory/blueplanet-inventory-fnr.git"
def mmRepo="https://bitbucket.ciena.com/scm/bp_inventory/blueplanet-inventory-metadata-modeller.git"
def gmRepo="https://bitbucket.ciena.com/scm/bp_inventory/blueplanet-inventory-gm.git"
def msArchetypeRepo="https://bitbucket.ciena.com/scm/bp_inventory/blueplanet-inventory-ms-installer.git"
def mmComponentName="mm"
def gmComponentName="gm"
def msArchetypeName="ms-archetype"
def fnrMap=[:]
def mmMap=[:]
def gmMap=[:]
def msArchetype=[:]
def releaseQualifier=""
def currentQualifier="-SNAPSHOT"
pipeline {
    options{
      timeout(time: 10 , unit: 'HOURS')
      ansiColor('gnome-terminal')
    }
    environment {
        TEMP_DIR="/tmp/${BUILD_TAG}"
        SLACK_CHANNEL_NAME='bpi-installer-automation-notification'    
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
                    if ("${components}".trim().length()==0){
                        echo "select components to build"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"  
                    }
                    else{
                        echo "selected component is ${components}"  
                    }                    
                    componentList="${components}".split('\\,')
                    for ( component in componentList ) {
                        if (gmComponentName==component && !"${GM_BRANCH}"){
                            echo "gm branch is missing in user input"
                            currentBuild.result == "FAILURE"
                            sh "exit 1"  
                        }  
                        else if(mmComponentName==component && !"${MM_BRANCH}"){
                            echo "mm branch is missing in user input"
                            currentBuild.result == "FAILURE"
                            sh "exit 1" 
                        }
                        else if(fnrComponentName==component && !"${FNR_BRANCH}"){
                            echo "fnr branch is missing in user input"
                            currentBuild.result == "FAILURE"
                            sh "exit 1"  
                        }
                    }
                    if(!"${BPI_MS_ARCHETYPE_BRANCH}"){
                        echo "Please priovide bpi ms archetype branch"
                        currentBuild.result == "FAILURE"
                        sh "exit 1"                        
                    }                    
                }
            }
        }
        stage ("get current versions"){
            steps{
                script{
                    if(isComponentPresentInRequest(fnrComponentName)){
                        dir("$TEMP_DIR/$fnrComponentName"){
                            checkout([$class: 'GitSCM', 
                            branches: [[name: "$FNR_BRANCH"]], 
                            extensions: [[$class: 'CloneOption', depth: 1, noTags: true, reference: '', shallow: true, timeout: 20]], 
                            gitTool: 'Default', 
                            userRemoteConfigs: [[credentialsId: '7a8c4d4e-ce1f-44af-b8dc-ae57baec9242', url: fnrRepo]]])
                            fnrPomContent = readMavenPom file: "pom.xml";   
                            fnrCurrentVersion=fnrPomContent.'version'
                        }
                        echo "fnr current version is $fnrCurrentVersion"  

                    }
                    if(isComponentPresentInRequest(mmComponentName)){
                        dir("$TEMP_DIR/$mmComponentName"){
                            checkout([$class: 'GitSCM', 
                            branches: [[name: "$MM_BRANCH"]], 
                            extensions: [[$class: 'CloneOption', depth: 1, noTags: true, reference: '', shallow: true, timeout: 20]], 
                            gitTool: 'Default', 
                            userRemoteConfigs: [[credentialsId: '7a8c4d4e-ce1f-44af-b8dc-ae57baec9242', url: mmRepo]]])
                            mmPomContent = readMavenPom file: "pom.xml";   
                            mmCurrentVersion=mmPomContent.'version'
                        }

                    }
                    if(isComponentPresentInRequest(gmComponentName)){
                        dir("$TEMP_DIR/$gmComponentName"){
                            checkout([$class: 'GitSCM', 
                            branches: [[name: "$GM_BRANCH"]], 
                            extensions: [[$class: 'CloneOption', depth: 1, noTags: true, reference: '', shallow: true, timeout: 20]], 
                            gitTool: 'Default', 
                            userRemoteConfigs: [[credentialsId: '7a8c4d4e-ce1f-44af-b8dc-ae57baec9242', url: gmRepo]]])
                            gmPomContent = readMavenPom file: "pom.xml";   
                            gmCurrentVersion=gmPomContent.'version'
                        }
                        echo "gm current version is $gmCurrentVersion"  
                    }
                    dir("$TEMP_DIR/$msArchetypeName"){
                            checkout([$class: 'GitSCM', 
                            branches: [[name: "$BPI_MS_ARCHETYPE_BRANCH"]], 
                            extensions: [[$class: 'CloneOption', depth: 1, noTags: true, reference: '', shallow: true, timeout: 20]], 
                            gitTool: 'Default', 
                            userRemoteConfigs: [[credentialsId: '7a8c4d4e-ce1f-44af-b8dc-ae57baec9242', url: msArchetypeRepo]]])
                            archetypePomContent = readMavenPom file: "bpi-ms-installer-archetype/pom.xml";   
                            archetypeCurrentVersion=archetypePomContent.properties.'blueplanet.bpinstaller.archetype.version'
                        } 

                    echo "archetype current version is $archetypeCurrentVersion"
                }
            }

        }
        stage("Manage archetype release"){
            when{
                beforeAgent true
                expression {
                    return params.RELEASE_ARCHETYPE
                }
            }
            steps{
                script{
                    echo "Archetype release will be handled here"
                }
            }

        }
        stage("Manage ms release"){
            when{
                beforeAgent true
                expression {
                    return params.RELEASE_MS
                }
            }
            steps{
                script{
                    echo "release will be handled here"
                }
            }

        }
        stage("calculate final version and branch"){
            steps{
                script{                    
                    if(isComponentPresentInRequest(fnrComponentName)){  
                        versinArr=fnrCurrentVersion.split('\\.')
                        majorVersion=versinArr[0]
                        minorVersion=versinArr[1]
                        if(versinArr[2].contains("-")){
                            incrementVersion=versinArr[2].split('\\-')[0]
                            currQualifier=versinArr[2].split('\\-')[1]
                        }else{
                            incrementVersion=versinArr[2]
                            currQualifier=versinArr[2].split('\\-')[1]
                        }
                        if("$RELEASE_MS".toBoolean()){
                            fnrMap['finalBranch']='v'+majorVersion+"."+minorVersion+"."+incrementVersion+"$RELEASE_QUALIFIER_MS".trim()
                            fnrMap['finalVersion']=majorVersion+"."+minorVersion+"."+incrementVersion+"$RELEASE_QUALIFIER_MS".trim()
                        }else{
                            fnrMap['finalBranch']="$FNR_BRANCH"
                            fnrMap['finalVersion']=fnrCurrentVersion
                        }                                             
                    }
                    if(isComponentPresentInRequest(mmComponentName)){  
                        versinArr=mmCurrentVersion.split('\\.')
                        majorVersion=versinArr[0]
                        minorVersion=versinArr[1]
                        if(versinArr[2].contains("-")){
                            incrementVersion=versinArr[2].split('\\-')[0]
                            currQualifier=versinArr[2].split('\\-')[1]
                        }else{
                            incrementVersion=versinArr[2]
                            currQualifier=versinArr[2].split('\\-')[1]
                        }
                        if("$RELEASE_MS".toBoolean()){
                            mmMap['finalBranch']='v'+majorVersion+"."+minorVersion+"."+incrementVersion+"$RELEASE_QUALIFIER_MS".trim()
                            mmMap['finalVersion']=majorVersion+"."+minorVersion+"."+incrementVersion+"$RELEASE_QUALIFIER_MS".trim()
                        }else{
                            mmMap['finalBranch']="$MM_BRANCH"
                            mmMap['finalVersion']=mmCurrentVersion
                        }                                             
                                                                
                    }
                    if(isComponentPresentInRequest(gmComponentName)){  
                        versinArr=gmCurrentVersion.split('\\.')
                        majorVersion=versinArr[0]
                        minorVersion=versinArr[1]
                        if(versinArr[2].contains("-")){
                            incrementVersion=versinArr[2].split('\\-')[0]
                            currQualifier=versinArr[2].split('\\-')[1]
                        }else{
                            incrementVersion=versinArr[2]
                            currQualifier=versinArr[2].split('\\-')[1]
                        }
                        if("$RELEASE_MS".toBoolean()){
                            gmMap['finalBranch']='v'+majorVersion+"."+minorVersion+"."+incrementVersion+"$RELEASE_QUALIFIER_MS".trim()
                            gmMap['finalVersion']=majorVersion+"."+minorVersion+"."+incrementVersion+"$RELEASE_QUALIFIER_MS".trim()
                        }else{
                            gmMap['finalBranch']="$GM_BRANCH"
                            gmMap['finalVersion']=gmCurrentVersion
                        }                                             
                    }
                    versinArr=archetypeCurrentVersion.split('\\.')
                    majorVersion=versinArr[0]
                    minorVersion=versinArr[1]
                    if(versinArr[2].contains("-")){
                        incrementVersion=versinArr[2].split('\\-')[0]
                        currQualifier=versinArr[2].split('\\-')[1]
                    }else{
                        incrementVersion=versinArr[2]
                        currQualifier=versinArr[2].split('\\-')[1]
                    }
                    if("$RELEASE_ARCHETYPE".toBoolean()){
                        msArchetype['finalBranch']='v'+majorVersion+"."+minorVersion+"."+incrementVersion+"$RELEASE_QUALIFIER_ARCHETYPE".trim()
                        msArchetype['finalVersion']=majorVersion+"."+minorVersion+"."+incrementVersion+"$RELEASE_QUALIFIER_ARCHETYPE".trim()
                    }else{
                        msArchetype['finalBranch']="$BPI_MS_ARCHETYPE_BRANCH"
                        msArchetype['finalVersion']=archetypeCurrentVersion
                    }        


                }
            }

        }
        stage("build ms archetype"){
            when{
                beforeAgent true
                expression {
                    return params.BUILD_MS_ARCHETYPE
                }
            }
            steps{
                script{
                    echo "building ms archetype revision "+msArchetype['finalBranch']
                    build(job: 'build-bpi-ms-installer-archetype',
                        propagate: true, parameters: [[$class: 'StringParameterValue',
                        name: 'git_branch', value: msArchetype['finalBranch']]
                    ])
                }
            }
        }
        stage("build ms"){
            when{
                beforeAgent true
                expression {
                    return params.BUILD_MS
                }
            }
            steps{
                script{
                    if(isComponentPresentInRequest(fnrComponentName)){
                        build(job: 'build-bpi-fnr',
                            propagate: true, parameters: [[$class: 'StringParameterValue',
                            name: 'git_branch', value: fnrMap['finalBranch']]
                        ])
                    }
                    if(isComponentPresentInRequest(mmComponentName)){
                        echo "build mm"
                        build(job: 'build-bpi-mm',
                             propagate: true, parameters: [[$class: 'StringParameterValue',
                            name: 'git_branch', value: mmMap['finalBranch']]
                        ])

                    }          
                    if(isComponentPresentInRequest(gmComponentName)){
                        echo "build gm"
                        build(job: 'build-bpi-gm',
                             propagate: true, parameters: [[$class: 'StringParameterValue',
                            name: 'git_branch', value: gmMap['finalBranch']]
                        ])
                    }            
                           
                }
            }
        }
        stage("build ms solutions"){
            steps{
                script{
                    archetypeVersion=msArchetype["finalVersion"]
                    if(isComponentPresentInRequest(fnrComponentName)){
                        solutionVersion=fnrMap['finalVersion']
                        echo "building fnr solution "+solutionVersion
                        withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){
                            sh """
                            export ANSIBLE_FORCE_COLOR=true 
                            ansible-playbook -vvv playbooks/cicd/bpi_ms_solution.yml --vault-password-file $ANSIBLEVAULT \\
                            -e component=$fnrComponentName -e bpi_ms_archetype_version=$archetypeVersion -e fnr_solution_version=$solutionVersion

                            """
                        }
                    }
                    if(isComponentPresentInRequest(mmComponentName)){
                        echo "building mm solution"
                        solutionVersion=mmMap['finalVersion']
                        echo "building mm solution "+solutionVersion
                        withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){

                            sh """
                            export ANSIBLE_FORCE_COLOR=true 
                            ansible-playbook -vvv playbooks/cicd/bpi_ms_solution.yml --vault-password-file $ANSIBLEVAULT \\
                            -e component=$mmComponentName -e bpi_ms_archetype_version=$archetypeVersion -e mm_solution_version=$solutionVersion

                            """
                        }
                    }
                    if(isComponentPresentInRequest(gmComponentName)){
                        echo "building gm solution"
                        solutionVersion=gmMap['finalVersion']
                        echo "building gm solution "+solutionVersion
                        withCredentials([file(credentialsId: 'ansible-vaultkey' , variable: 'ANSIBLEVAULT')]){

                            sh """
                            export ANSIBLE_FORCE_COLOR=true 
                            ansible-playbook -vvv playbooks/cicd/bpi_ms_solution.yml --vault-password-file $ANSIBLEVAULT \\
                            -e component=$gmComponentName -e bpi_ms_archetype_version=$archetypeVersion -e gm_solution_version=$solutionVersion

                            """
                        }
                    }
                    
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
            sh 'sudo rm -rf $TEMP_DIR'
            deleteDir()
        }
    }

    
}

def isComponentPresentInRequest(component){
    componentList="${components}".split('\\,')
    isPresent=false
    for (ms in componentList){
        if(ms==component){
            return true
        }
    }
    return isPresent

}
