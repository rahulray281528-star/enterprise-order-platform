// Declarative pipeline for the platform.
//
// Nothing here is required to develop locally - `mvn clean verify` and
// `docker compose up` do everything this pipeline does. It exists to show the
// intended promotion path and is documented in jenkins/README.md.
pipeline {
    agent any

    tools {
        jdk 'jdk-21'
        maven 'maven-3.9'
    }

    environment {
        MAVEN_OPTS = '-Dmaven.repo.local=.m2/repository -Xmx1024m'
        IMAGE_TAG  = "${env.BUILD_NUMBER}"
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 45, unit: 'MINUTES')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Compile') {
            steps {
                sh 'mvn -B clean compile'
            }
        }

        stage('Unit tests') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration tests') {
            steps {
                // Testcontainers needs a reachable Docker daemon on the agent.
                sh 'mvn -B verify -DskipUTs'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/*.xml'
                }
            }
        }

        stage('Static analysis') {
            parallel {
                stage('Checkstyle') {
                    steps { sh 'mvn -B checkstyle:check -Pquality' }
                }
                stage('SpotBugs') {
                    steps { sh 'mvn -B spotbugs:check -Pquality' }
                }
            }
        }

        stage('Coverage') {
            steps {
                sh 'mvn -B jacoco:report'
                publishHTML(target: [
                    reportDir  : 'order-service/target/site/jacoco',
                    reportFiles: 'index.html',
                    reportName : 'JaCoCo Coverage',
                    allowMissing: true,
                    keepAll    : true,
                    alwaysLinkToLastBuild: true
                ])
            }
        }

        stage('SonarQube') {
            when {
                expression { return env.SONAR_HOST_URL?.trim() }
            }
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh 'mvn -B sonar:sonar -Dsonar.projectKey=enterprise-order-platform'
                }
            }
        }

        stage('Package') {
            steps {
                sh 'mvn -B package -DskipTests'
                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, allowEmptyArchive: true
            }
        }

        stage('Docker build') {
            steps {
                script {
                    def services = [
                        'service-discovery', 'config-server', 'api-gateway',
                        'auth-service', 'product-service', 'inventory-service',
                        'order-service', 'payment-service', 'notification-service'
                    ]
                    services.each { svc ->
                        sh "docker build -f ${svc}/Dockerfile -t enterprise/${svc}:${IMAGE_TAG} ."
                    }
                }
            }
        }

        stage('Image validation') {
            steps {
                sh 'docker compose config --quiet'
                sh 'docker images | grep enterprise/ || true'
            }
        }
    }

    post {
        always {
            cleanWs(deleteDirs: true, notFailBuild: true)
        }
        failure {
            echo "Build ${env.BUILD_NUMBER} failed - see the stage log above."
        }
    }
}
