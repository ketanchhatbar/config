pipeline {

    agent any
    parameters {
    	string(defaultValue: 'ketan.chhatbar', name: 'openShiftCredentialsId')
    	string(defaultValue: 'https://console.starter-us-west-2.openshift.com', name: 'openShiftURL')
    	string(defaultValue: 'deployments', name: 'deploymentsFileId')
    }
    stages {
     	stage('Prepare') {
            steps {
            	script {
            		ocDir = tool 'oc3.11.0'
            		deploymentsFileId = params.deploymentsFileId
            		envMap = null
            		envMap = getEnvMapping(deploymentsFileId)
            		echo "${envMap}" // environment -> openshift namespace mapping
            	}
            }
     	}
        stage('Deploy ConfigMaps') {
            steps {
            	script {
                    withCredentials([usernamePassword(credentialsId: params.openShiftCredentialsId, usernameVariable: 'user', passwordVariable: 'token')]) {
                    	sh "${ocDir}/oc login --token='${env.token}' ${params.openShiftURL} --insecure-skip-tls-verify"
                        envMap.each { envr, namespaces ->
                        	echo "echo environment = ${envr}"
                        	for(String namespace : namespaces) {
                                sh "${ocDir}/oc project ${namespace}"
                        		def FILES_LIST = sh (script: "ls ./openshift-secrets-configmaps/${envr}/configMaps", returnStdout: true).trim()
                        		for(String ele : FILES_LIST.split("\\r?\\n")) { 
                        			try {
                        				sh "${ocDir}/oc delete configmap ${ele}"
                        			} catch(err) {
                        				echo 'Error configmap does not exist'
                        				echo "${err}"
                        			}
                        			try {
                                		sh "${ocDir}/oc create configmap ${ele} --from-file=./${envr}/configMaps/${ele}"
                                	} catch(err) {
                                		echo "Error creating configmap ${ele}"
                                		error "${err}"
                                	}   
                                }
                        	}
                        }
                    }
            	}
            }
	    }

	    stage('Deploy Secrets') {
      		steps {
            	script {
            		withCredentials([usernamePassword(credentialsId: params.openShiftCredentialsId, usernameVariable: 'user', passwordVariable: 'token')]) {
                    	sh "${ocDir}/oc login --token='${env.token}' ${params.openShiftURL} --insecure-skip-tls-verify"
                        envMap.each { envr, namespaces ->
                        	echo "echo environment = ${envr}"
                        	for(String namespace : namespaces) {
                                sh "${ocDir}/oc project ${namespace}"
                        		def FILES_LIST = sh (script: "ls ./openshift-secrets-configmaps/${envr}/secrets", returnStdout: true).trim()
                        		for(String ele : FILES_LIST.split("\\r?\\n")) { 
                        			try {
                        				sh "${ocDir}/oc delete secret ${ele}"
                        			} catch(err) {
                        				echo 'Error secret does not exist'
                        				echo "${err}"
                        			}
                        			try {
                                		sh "${ocDir}/oc create secret generic ${ele} --from-file=./${envr}/secrets/${ele}"
                                	} catch(err) {
                                		echo "Error creating secret ${ele}"
                                		error "${err}"
                                	}   
                                }
                        	}
                        }
                    }

            	}
      		}       
	    }
    }

}

def getEnvMapping(deploymentsFileId) {
	def envMap = [:]
	def envr = null
	def namespace = null
    configFileProvider([configFile(fileId: deploymentsFileId, variable: 'DEPLOYMENTS')]) {
    	def deployments = readJSON file: env.DEPLOYMENTS
    	deployments.each { track, specs ->
        	if(track == "develop" || track == "master" || track == "release") {
            	for(int i = 0; i < specs.size(); i++) {
            		envr = specs[i].environment
                	namespace = specs[i].namespace
                	if(envMap.get(envr) == null) {
                		envMap.put(envr, [])
                		envMap.get(envr).add(namespace)
                	} else {
                		if(!envMap.get(envr).contains(namespace)) {
                			envMap.get(envr).add(namespace)
                		}
                	}
            	}
        	}
    	}
    }
    return envMap
}
