import groovy.json.JsonOutput
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
def readpom(file_path,bpi_qualifier, fc_qualifier, bpmn_qualifier,dev_build,bpi_branch) {

    pom_ver = [:]
    read_pom = readMavenPom file: "${file_path}";

    bpi_pom_ver = read_pom.properties.'blueplanet.inventory.version'.split('-')[0]
    core_pom_ver = read_pom.parent.version.split('-')[0]
    bpm_pom_ver = read_pom.properties.'blueplanet.inventory.bpmn.version'.split('-')[0]
    echo "dev build value is "+dev_build
    echo "bpi branch is "+bpi_branch
    if (bpi_qualifier){
        if(dev_build.toString()=="true"){
            pom_ver["tag_bpi_version_to"]=bpi_branch
        }else{
            pom_ver["tag_bpi_version_to"] = 'v'+bpi_pom_ver + bpi_qualifier;
        }
        
        pom_ver["update_bpi_pom_from"] = read_pom.properties.'blueplanet.inventory.version';
        pom_ver['update_bpi_pom_to'] = bpi_pom_ver + bpi_qualifier;

        pom_ver["update_core_pom_from"] = read_pom.parent.version;

        if (fc_qualifier){

            pom_ver["update_core_pom_to"] = fc_qualifier;

        }else{

            echo "Please provide release_core_ui_version parameter in build."
        }

        pom_ver["update_bpmn_pom_from"] = read_pom.properties.'blueplanet.inventory.bpmn.version';

        if (bpmn_qualifier){

            pom_ver["update_bpmn_pom_to"] = bpmn_qualifier;
            
        }else{

            echo "Please provide release_bpmn_version parameter in build."
        }

    }else {

        release = '-SNAPSHOT'
        inv_temp = bpi_pom_ver[-1].toInteger()+1

        if (fc_qualifier){

            core_temp = fc_qualifier[-1].toInteger()+1

        }else{

             echo "Please provide release_core_ui_version parameter in build."
        }

        if (bpmn_qualifier){

            bpmn_temp = bpmn_qualifier[-1].toInteger()+1
            
        }else{

            echo "Please provide release_bpmn_version parameter in build."
        }

        echo "dev build value is "+dev_build
        echo "bpi branch is "+bpi_branch
        if(dev_build=="true"){
            pom_ver["tag_bpi_version_to"]=bpi_branch
        }else{
            pom_ver["tag_bpi_version_to"] = 'v'+bpi_pom_ver;
        }        
        pom_ver["update_bpi_pom_from"] = read_pom.properties.'blueplanet.inventory.version';
        pom_ver['update_bpi_pom_to'] = bpi_pom_ver;

        pom_ver["update_core_pom_from"] = read_pom.parent.version;
        pom_ver["update_core_pom_to"] = fc_qualifier;

        pom_ver["update_bpmn_pom_from"] = read_pom.properties.'blueplanet.inventory.bpmn.version';
        pom_ver["update_bpmn_pom_to"] = bpmn_qualifier;

        pom_ver['update_bpi_pom_to_git_branch'] = pom_ver['update_bpi_pom_to'][0..-2]+ inv_temp.toString()+release
        pom_ver['update_core_pom_to_git_branch'] = pom_ver['update_core_pom_to'][0..-2]+ core_temp.toString()+release
        pom_ver['update_bpmn_pom_to_git_branch'] = pom_ver['update_bpmn_pom_to'][0..-2]+ bpmn_temp.toString()+release

    }
    
    pom_ver['release_ver_major'] = pom_ver['update_bpi_pom_to'].split("\\.")[0]
    pom_ver['release_ver_minor'] = pom_ver['update_bpi_pom_to'].split("\\.")[1]
    if (bpi_qualifier){
        pom_ver['release_ver_increment'] = pom_ver['update_bpi_pom_to'].split("-")[0].split("\\.")[2]
    }else{
        pom_ver['release_ver_increment'] = pom_ver['update_bpi_pom_to'].split("\\.")[2]
    }
    pom_ver['item_in_release'] = pom_ver['release_ver_major'] + pom_ver['release_ver_minor']
    
    writeJSON(file: 'git_versions.json', json: pom_ver)
    print("###########################")
    print(pom_ver)
    print("###########################")
    echo "release qualifier is "+"${bpi_release_qualifier}"
    return pom_ver
}
def newVmIp=""
def release_orchestrator_branch="${cicd_orchestrator_branch}"
def release_orchestrator_event="${cicd_orchestrator_event}"
def allEventName="all"
timeout(time: 20, unit: 'HOURS'){
node("${jenkins_node}") {

    withEnv(['AWS_REGION="ap-south-1"', 'AWS_SUBNET="subnet-0ad75e12a1deb3210"']) {

        ansiColor('gnome-terminal'){

            try{
                stage ("validate parameters"){
                    script{
                        if(!"${installer_archetype_version}" && params.bpi_build_archetype==false){
                            echo "Please priovide archetype version or check bpi_build_archetype checkbox."
                            currentBuild.result == "FAILURE"
                            sh "exit 1"                        
                        }else if("${installer_archetype_version}" && params.bpi_build_archetype){
                            echo "Please priovide either archetype version or check bpi_build_archetype checkbox. One cannot do both"
                            currentBuild.result == "FAILURE"
                            sh "exit 1"     
                        }
                    }
                }
                stage ('calculate versions') 
                {
                    checkout([$class: 'GitSCM', branches: [[name: '${tag_bpi_version_from}']], extensions: [[$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true, timeout: 20]], gitTool: 'Default', userRemoteConfigs: [[credentialsId: '6400e9b6-d2c9-4759-aba9-d36ebb9425d7', url: 'https://bitbucket.ciena.com/scm/bp_inventory/blueplanet-inventory.git']]])
                    
                    readpom('planning-federation/app-server/blueplanet-inventory-rest-archetype/src/main/resources/archetype-resources/pom.xml',"${bpi_release_qualifier}","${release_core_ui_version}","${release_bpmn_version}",params.DEV_BUILD,params.tag_bpi_version_from)
                }
                stage ('checkout SCM') 
                {
                    checkout([$class: 'GitSCM', branches: [[name: '${dr_infra_version}']], extensions: [], gitTool: 'Default', userRemoteConfigs: [[credentialsId: '6f94116a-b375-47dd-b050-739213759120', url: 'https://git.blueplanet.com/Ciena/BPI/infrastructure/dr-infra.git']]])
                }
                stage ('create tag') 
                {
                    if( !params.DEV_BUILD && (  "${release_orchestrator_event}"== "bpi_publish_sdk" || "${release_orchestrator_event}"== "${allEventName}")){

                        ansiblePlaybook colorized: true, credentialsId: 'bpidev-bpi-devops-dr', extras: '-vvv -e \'{"tag_bpi_version_from":"${tag_bpi_version_from}","GA_Release":"false","projects_git":["graph-api","planning-federation","routing","tmf-integration","asset-sdk"]}\'', installation: 'ansible2.8.5', playbook: 'playbooks/products/bpi/pb-bpi-release-main-git.yml', vaultCredentialsId: 'ansible-vaultkey'

                    }else{
                        Utils.markStageSkippedForConditional('create tag')
                    }
                }
                if(params.Build_BPI_Artifacts){
                    
                    stage ('build artifacts')
                    {
                        if("${release_orchestrator_event}"== "bpi_publish_sdk" || "${release_orchestrator_event}"== "${allEventName}"){

                            if("${pom_ver['release_ver_major']}".toInteger()>=22 && "${pom_ver['release_ver_minor']}".toInteger()>=2){
                                
                                if("${pom_ver['release_ver_major']}".toInteger()>=23){

                                    build job: 'fusion-inventory-23.4.X-SNAPSHOT', parameters: [string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]

                                }else{

                                    build job: 'fusion-inventory-22.2.X-SNAPSHOT', parameters: [string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                }
                                
                                build job: 'blueplanet-routing-22.2.X-SNAPSHOT', parameters: [string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                
                                build job: 'build-tmf-22.2.X-SNAPSHOT-pre', 
                                parameters: [string(name: 'profile', value: 'bpinst,ci'), string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                
                                if("${pom_ver['item_in_release']}".toInteger()<228){
                                    
                                    build job: 'build-bpi-22.2.X-SNAPSHOT', 
                                    parameters: [extendedChoice(name: 'components-to-build', value: 'bpi-neo4j,bpi-postgres,deployment-tools,bpi-flexnet,bpi-ui-npm-package,test-module'), 
                                    string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]

                                    build job: 'build-bpi-bpinst-22.2.X-SNAPSHOT', 
                                    parameters: [extendedChoice(name: 'components-to-build', value: 'bpi-ui-showcase,bpi-tmf-showcase,archetype,asset-sdk-samples'), 
                                    string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                    
                                }else{
                                    
                                    build job: 'build-bpi-23.4.X-SNAPSHOT', 
                                    parameters: [extendedChoice(name: 'components-to-build', value: 'bpi-neo4j,bpi-postgres,deployment-tools,bpi-flexnet,bpi-ui-npm-package,test-module,bpi-localization-bundles'), 
                                    string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]

                                    build job: 'build-bpi-bpinst-23.4.X-SNAPSHOT', 
                                    parameters: [extendedChoice(name: 'components-to-build', value: 'bpi-ui-showcase,bpi-tmf-showcase,archetype,asset-sdk-samples'), 
                                    string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                }
                                
                                
                                
                            }else if("${pom_ver['release_ver_major']}".toInteger()==21 && "${pom_ver['release_ver_minor']}".toInteger()==10){
                                
                                build job: 'BPI-Release-Build-fusion-inventory-21.10+', parameters: [string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                build job: 'BPI-Release-Build-blueplanet-routing-21.10+', parameters: [string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                
                                build job: 'BPI-Release-Build-TMF-pre-21.10+', 
                                parameters: [string(name: 'profile', value: 'bpinst,ci'), string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                
                                build job: 'BPI-Release-Build-bpi-21.10+', 
                                parameters: [extendedChoice(name: 'components-to-build', 
                                value: 'bpi-neo4j,bpi-postgres,deployment-tools,bpi-ui-npm-package,bpi-flexnet,test-module,archetype,bpi-rest'), 
                                string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                
                                build job: 'BPI-Release-Build-bpinst-21.10+', 
                                parameters: [extendedChoice(name: 'components-to-build', value: 'bpi-ui-showcase,bpi-tmf-showcase,asset-sdk-samples'), 
                                string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                
                                build job: 'BPI-Release-Build-TMF-post-21.10+', parameters: [string(name: 'profile', value: 'bpinst,ci'), 
                                string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                                
                                build job: 'BPI-Release-Build-TMF-post-21.10+', parameters: [string(name: 'profile', value: 'bpinst,flexnet,ci'), 
                                string(name: 'git_branch', value: "${pom_ver['tag_bpi_version_to']}")]
                            }
                        }else{
                            Utils.markStageSkippedForConditional('build artifacts')
                        }
                    }
                }
                if(params.Verify_Build_Artifcats){

                    stage ('verify build artifacts'){

                        if("${release_orchestrator_event}"== "bpi_publish_sdk" || "${release_orchestrator_event}"== "${allEventName}"){
                            echo "skipping build check artifacts.."
                            // build job: 'util-check-builtartifacts', 
                            // parameters: [string(name: 'input_product_line', value: 'bpinventory'), string(name: 'release', value: "${pom_ver['update_bpi_pom_to']}"), 
                            // string(name: 'var_lineup_fc_solverbase', value: "${pom_ver['update_core_pom_to']}")]
                        }else{
                            Utils.markStageSkippedForConditional('verify build artifacts')
                        }
                    }
                }
                if(params.Build_Solution_Artifacts){
                    stage("build archetype"){
                        if("${release_orchestrator_event}"== "bpi_publish_sdk" ||  "${release_orchestrator_event}"== "${allEventName}"){
                            script{
                                if(!"${installer_archetype_version}" && params.bpi_build_archetype){
                                    def b = build job: 'bp_installer_archetype_release_pipeline', parameters: [ string(name: 'ARCHETYPE_RELEASE_VERSION', value: "${ARCHETYPE_RELEASE_VERSION}"), string(name: 'ARCHETYPE_BRANCH', value: "${ARCHETYPE_BRANCH}"), string(name: 'dr_infra_version', value: "${dr_infra_version}"), string(name: 'jenkins_node', value: "${jenkins_node}")]
                                    env.new_arch_version = b.getBuildVariables()["new_arch_version"]
                                    installer_archetype_version = env.new_arch_version
                                }
                            }
                        }else{
                            Utils.markStageSkippedForConditional('build archetype')
                        }
                    }
                    stage ('build solution'){
                        script{
                            if("${release_orchestrator_event}"== "bpi_publish_test_sol" || "${release_orchestrator_event}"== "${allEventName}"){

                                build job: 'solution-automation', parameters: [string(name: 'product_line', value: 'bpinventory'), string(name: 'release_ver_major', value: "${pom_ver['release_ver_major']}"), 
                                string(name: 'release_ver_minor', value: "${pom_ver['release_ver_minor']}"), string(name: 'release_ver_increment', value: "${pom_ver['release_ver_increment']}"), 
                                string(name: 'release_ver_qualifier', value: "${bpi_release_qualifier}"), string(name: 'installer_archetype_version', value: "${installer_archetype_version}"), 
                                booleanParam(name: 'allow_open_neo4j_port', value: false), booleanParam(name: 'build_flexnet', value: true), string(name: 'testing_prefix', value: "${testing_prefix}"), 
                                booleanParam(name: 'download_archetype', value: false), string(name: 'var_lineup_fc_solverbase', value: "${pom_ver['update_core_pom_to']}"), 
                                booleanParam(name: 'publish_bp2', value: "${publish_bp2}"), booleanParam(name: 'publish_helm', value: "${publish_helm}")]
                            }else{
                                Utils.markStageSkippedForConditional('build solution')
                            }
                        }
                    }
                }
                if(params.Create_Release_Environment){
                    parallel bp2Env: {
                        stage ('create bp2 environment'){

                            if("${release_orchestrator_event}"== "bpi_publish_regression_test" || "${release_orchestrator_event}"== "${allEventName}"){

                                script {

                                    INSTALLATION_AUTOMATION_JOB_RESULT = build( job: 'installation-automation-onlinemode', 
                                            parameters: [string(name: 'autovm_comments', value: "BPI-Release-${pom_ver['release_ver_major']}.${pom_ver['release_ver_minor']}.${pom_ver['release_ver_increment']}${bpi_release_qualifier}"), 
                                            string(name: 'release_ver_major', value: "${pom_ver['release_ver_major']}"), string(name: 'release_ver_minor', value: "${pom_ver['release_ver_minor']}"), 
                                            string(name: 'release_ver_increment', value: "${pom_ver['release_ver_increment']}"), string(name: 'var_lineup_xp_solverbase', value: "${var_lineup_xp_solverbase}"), 
                                            string(name: 'var_lineup_fc_solverbase', value: "${pom_ver['update_core_pom_to']}"), extendedChoice(name: 'product_line', value: 'bpinventory'), 
                                            string(name: 'release_ver_qualifier', value: "${bpi_release_qualifier}"), string(name: 'env_owner', value: 'Artem'), string(name: 'testing_prefix', value: "${testing_prefix}")]
                                    )
                                }
                            }else{
                                Utils.markStageSkippedForConditional('create bp2 environment')
                            }
                        }
                    },
                    eksEnv: {
                        stage('create eks environment'){

                            if("${pom_ver['release_ver_major']}".toInteger()>=23 && ("${release_orchestrator_event}"== "bpi_publish_regression_test" || "${release_orchestrator_event}"== "${allEventName}")){

                                // build job: 'bpi-k8s-installation', parameters: [string(name: 'release_ver_major', value: "${pom_ver['release_ver_major']}"), string(name: 'release_ver_minor', value: "${pom_ver['release_ver_minor']}"), 
                                // string(name: 'Cluster_name', value: "bpi-release-eks-${pom_ver['release_ver_major']}-${pom_ver['release_ver_minor']}-${pom_ver['release_ver_increment']}"), 
                                // string(name: 'dr_infra_branch', value: 'master'), string(name: 'k8s_provider', value: 'eks'), string(name: 'Region', value: 'us-east-1'), string(name: 'Cluster_type', value: 'ha'), 
                                // string(name: 'jenkins_node', value: 'solution-maker'), string(name: 'lineup', value: 'bpi'), string(name: 'lineup_version', value: ''), string(name: 'Core_platform_version', value: ''), 
                                // string(name: 'site_config', value: ''), string(name: 'staticnbi', value: ''), string(name: 'CHART_REPO', value: 'dev'), string(name: 'BPI_CHART_VERSION', value: "${pom_ver['update_bpi_pom_to']}"), 
                                // string(name: 'FC_CHART_VERSION', value: "${release_core_ui_version}"), string(name: 'BPMN_CHART_VERSION', value: "${release_bpmn_version}"), string(name: 'XP_CHART_VERSION', value: "${var_lineup_xp_solverbase}"),
                                // booleanParam(name: 'USE_FC_TEST_SOL', value: false), booleanParam(name: 'USE_BPI_TEST_SOL', value: false), booleanParam(name: 'USE_BPMN_TEST_SOL', value: false)]
                                echo "create eks environment"
                            }else{
                                Utils.markStageSkippedForConditional('create eks environment')
                            }
                        }
                    },
                    aksEnv: {
                        stage('create aks environment'){

                            if("${pom_ver['release_ver_major']}".toInteger()>=23 && ("${release_orchestrator_event}"== "bpi_publish_regression_test" || "${release_orchestrator_event}"== "${allEventName}")){

                                build job: 'bpi-k8s-installation', parameters: [string(name: 'release_ver_major', value: "${pom_ver['release_ver_major']}"), string(name: 'release_ver_minor', value: "${pom_ver['release_ver_minor']}"), 
                                string(name: 'Cluster_name', value: "bpi-release-aks-${pom_ver['release_ver_major']}-${pom_ver['release_ver_minor']}-${pom_ver['release_ver_increment']}"), 
                                string(name: 'dr_infra_branch', value: 'master'), string(name: 'k8s_provider', value: 'aks'), string(name: 'Region', value: 'eastus2'), string(name: 'Cluster_type', value: 'ha'), 
                                string(name: 'jenkins_node', value: 'solution-maker'), string(name: 'lineup', value: 'discovery'), string(name: 'lineup_version', value: ''), string(name: 'Core_platform_version', value: ''), 
                                string(name: 'site_config', value: ''), string(name: 'staticnbi', value: ''), string(name: 'CHART_REPO', value: 'dev'), string(name: 'BPI_CHART_VERSION', value: "${pom_ver['update_bpi_pom_to']}"), 
                                string(name: 'FC_CHART_VERSION', value: "${release_core_ui_version}"), string(name: 'BPMN_CHART_VERSION', value: ""), string(name: 'XP_CHART_VERSION', value: ""),
                                booleanParam(name: 'USE_FC_TEST_SOL', value: false), booleanParam(name: 'USE_BPI_TEST_SOL', value: false), booleanParam(name: 'USE_BPMN_TEST_SOL', value: false)]
                                echo "create aks environment"
                            }else{
                                Utils.markStageSkippedForConditional('create aks environment')
                            }
                        }
                    }
                }
                stage ('wait for nagios to clear'){

                    if("${pom_ver['release_ver_major']}".toInteger()>=23 && ("${release_orchestrator_event}"== "bpi_publish_regression_test" || "${release_orchestrator_event}"== "${allEventName}")){
                        script {
                            
                            new_vm_ip = sh(script:"""
                                    set -x
                                    aws ec2 --region "${AWS_REGION}" describe-instances --filters \"Name=tag:it_jenkins_build,Values=jenkins-installation-automation-onlinemode-\"${INSTALLATION_AUTOMATION_JOB_RESULT.getId()}\"\" --query \"Reservations[*].Instances[*].{ip:PrivateIpAddress}\" --output json | jq -r '.[0][0].ip' """,returnStdout:true)
                            echo "ip of launched aws ec2 is "+new_vm_ip
                            newVmIp = new_vm_ip.replaceAll("\\s","")
                            //sleep 3000
                            sh "python3 jenkins_pipeline/common/nagios_check.py --bpHost $newVmIp --retries 500"
                            echo "nagios check"
                        }
                    }else{
                        Utils.markStageSkippedForConditional('wait for nagios to clear')
                    }
                }
                // stage ('environment for regression'){

                //     if("${pom_ver['release_ver_major']}".toInteger()>=23 && ("${release_orchestrator_event}"== "bpi_publish_regression_test" || "${release_orchestrator_event}"== "${allEventName}")){
                //         script{
                            
                //             build job: 'Prepare_env_for_regression', 
                //             parameters: [string(name: 'target_vm_ip', value: "${newVmIp}"), string(name: 'version', value: "${pom_ver['update_bpi_pom_to']}"), booleanParam(name: 'LOAD_TEST_DATA', value: true), 
                //             booleanParam(name: 'CREATE_KAFKA_TOPICS', value: true), string(name: 'dr_infra_version', value: 'master'), string(name: 'jenkins_node', value: 'solution-maker')]
                //         }
                //     }else{
                //         Utils.markStageSkippedForConditional('environment for regression')
                //     }
                // }
                // stage ('Run regression-1 on launched VM'){

                //     if("${pom_ver['release_ver_major']}".toInteger()>=23 && ("${release_orchestrator_event}"== "bpi_publish_regression_test" || "${release_orchestrator_event}"== "${allEventName}")){

                //         script{
                //             catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                
                //                 build job: 'BPI autotests (all) 23.04 PLATFORM-APPBAR', parameters: [string(name: 'target.env', value: "${newVmIp}"), extendedChoice(name: 'browser', value: 'chrome'), 
                //                 extendedChoice(name: 'testScenario', value: '@prepareDB'),  extendedChoice(name: 'profile', value: 'e2e-jenkins-platform-appbar'), extendedChoice(name: 'url', value: 'blueplanet-app-bar-ui'), 
                //                 booleanParam(name: 'populateTestRailResults', value: false)]
                //             }
                //         }
                //     }else{
                //         Utils.markStageSkippedForConditional('Run regression-1 on launched VM')
                //     }
                // }
                // stage ('Run regression-2 on launched VM'){

                //     if("${pom_ver['release_ver_major']}".toInteger()>=23 && ("${release_orchestrator_event}"== "bpi_publish_regression_test" || "${release_orchestrator_event}"== "${allEventName}")){

                //         if((currentBuild.currentResult=='UNSTABLE' || currentBuild.currentResult=='SUCCESS')){
                    
                //             script{
                //                 catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                
                //                     build job: 'BPI autotests (all) 23.04 PLATFORM-APPBAR', parameters: [string(name: 'target.env', value: "${newVmIp}"), extendedChoice(name: 'browser', value: 'chrome.parallel'), 
                //                     extendedChoice(name: 'testScenario', value: '@all'),  extendedChoice(name: 'profile', value: 'e2e-test-jenkins-binary'), extendedChoice(name: 'url', value: 'blueplanet-app-bar-ui'), 
                //                     booleanParam(name: 'populateTestRailResults', value: false)]
                //                 }
                //             }
                //         }else{
                //             sh 'exit 1'
                //         }
                //     }else{
                //         Utils.markStageSkippedForConditional('Run regression-2 on launched VM')
                //     }
                // }
                stage ('Repack solution') {

                    if("${testing_prefix}" && ("${release_orchestrator_event}"== "bpi_publish_sol_sdk" || "${release_orchestrator_event}"== "${allEventName}")) {

                        if((currentBuild.currentResult=='UNSTABLE' || currentBuild.currentResult=='SUCCESS')){

                            build job: 'util-repacksolution', parameters: [string(name: 'release_ver_major', value: "${pom_ver['release_ver_major']}"), 
                            string(name: 'release_ver_minor', value: "${pom_ver['release_ver_minor']}"), string(name: 'release_ver_increment', value: "${pom_ver['release_ver_increment']}"), 
                            string(name: 'release_ver_qualifier', value: "${bpi_release_qualifier}"), string(name: 'prefix_toremove', value: "${testing_prefix}"), string(name: 'product_line', value: 'bpinventory'),
                            string(name: 'SOLUTION_TYPE', value: 'bp2')]
                        }else{
                            sh 'exit 1'
                        }
                    }else{
                        Utils.markStageSkippedForConditional('Repack solution')
                    }
                }
                stage ('update pom and raise PR') 
                {
                    if( !params.DEV_BUILD && (!"${bpi_release_qualifier}" && ("${release_orchestrator_event}"== "bpi_publish_sol_sdk" || "${release_orchestrator_event}"== "${allEventName}"))) {
                        
                        ansiblePlaybook colorized: true, credentialsId: 'bpidev-bpi-devops-dr', extras: '-vvv -e \'{"tag_bpi_version_from":"${tag_bpi_version_from}","GA_Release":"true","projects_git":["graph-api","planning-federation","routing","tmf-integration","asset-sdk"]}\'', installation: 'ansible2.8.5', playbook: 'playbooks/products/bpi/pb-bpi-release-main-git.yml', vaultCredentialsId: 'ansible-vaultkey' 
                    
                    }else{
                        Utils.markStageSkippedForConditional('update pom and raise PR')
                    }
                }
            }catch (InterruptedException e){
                echo "Got interrupt"
                currentBuild.result = 'ABORTED'
                throw e
            }catch ( Throwable e){
                echo "got error in build"
                echo "Caught ${e.toString()}"
                currentBuild.result = 'FAILURE'
                throw e
            }finally{
                echo "applying post actions"
                def currentResult = currentBuild.result ?: 'SUCCESS'
                if (currentBuild.result == 'UNSTABLE'){
                    currentResult = 'SUCCESS'
                }
                script {
                    // echo currentBuild.result
                    general_slack_notification("bpi-test",currentResult)
                    BRANCH_NAME="${release_orchestrator_branch}"
                    EVENT_NAME="${release_orchestrator_event}"
                    VERSION_PUBLISHED="${pom_ver['update_bpi_pom_to']}"
                    RELEASE_VERSION="${pom_ver['update_bpi_pom_to']}"
                    CURRENT_BUILD=env.BUILD_ID
                    if ("${release_orchestrator_branch}"){
                        sh(script:"""
                            set -x
                            bash jenkins_pipeline/common/push_events.sh $BRANCH_NAME $EVENT_NAME $VERSION_PUBLISHED $RELEASE_VERSION ${currentResult} $CURRENT_BUILD

                        """)
                    }
                }
                stage("Archetype Version used"){
                    echo "${installer_archetype_version}"
                }
                stage ('clean workspace'){
                    
                    cleanWs()
                }
            }
        }    
    }
}
}
// def getRegressionJobName(major_version,minor_version){
//     if(major_version==21 && minor_version==6 ){
//         return "BPI autotests (by feature tags) 21.06 PLATFORM-APPBAR"
//     }else if(major_version==21 && minor_version==10){
//         return "BPI autotests (by feature tags) 21.10 PLATFORM-APPBAR"
//     }else if(major_version==22 && minor_version==2){
//         return "BPI autotests (by feature tags) 22.02 PLATFORM-APPBAR"
//     }else if(major_version==22 && minor_version==8){
//         return "BPI autotests (by feature tags) 22.08 PLATFORM-APPBAR"
//     }else if(major_version==22 && minor_version==12){
//         return "BPI autotests (by feature tags) 22.12 PLATFORM-APPBAR"
//     }else if(major_version==23 && minor_version==4){
//         return "BPI autotests (by feature tags) 23.04 PLATFORM-APPBAR"
//     }
//     return ""

// }

def general_slack_notification(channel_name,build_status) {
    slackSend(channel: "${channel_name}", message: "${env.JOB_NAME} Build number ${env.BUILD_NUMBER}  completed with status ${build_status}, please check ${env.BUILD_URL} for more details")
}

return this
