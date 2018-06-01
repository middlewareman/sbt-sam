import sbt._

object LibraryDependencies {
  // versions
  val awsSdkVersion = "1.11.338"
  val awsXrayVersion = "1.3.1"
  val akkaVersion = "2.5.13"

  // libraries
  val libAwsJavaSdk: ModuleID = "com.amazonaws" % "aws-java-sdk" % awsSdkVersion
  val libAwsDynamoDBSdk: ModuleID = "com.amazonaws" % "aws-java-sdk-dynamodb" % awsSdkVersion
  val libAwsSnsSdk: ModuleID = "com.amazonaws" % "aws-java-sdk-sns" % awsSdkVersion
  val libAwsS3Sdk: ModuleID = "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion
  val libAwsKinesisSdk: ModuleID = "com.amazonaws" % "aws-java-sdk-kinesis" % awsSdkVersion
  val libAwsEcsSdk: ModuleID = "com.amazonaws" % "aws-java-sdk-ecs" % awsSdkVersion
  val libAwsSecretsManagerSdk: ModuleID = "com.amazonaws" % "aws-java-sdk-secretsmanager" % awsSdkVersion

  //
  // AWS X-Ray
  //
  // Basic functionality for creating segments and transmitting segments. Includes AWSXRayServletFilter for instrumenting incoming requests.
  val libAwsXRayCoreSdk: ModuleID = "com.amazonaws" % "aws-xray-recorder-sdk-core" % awsXrayVersion
  // Instruments outbound HTTP calls made with Apache HTTP clients.
  val libAwsXRayApacheHttpSdk: ModuleID = "com.amazonaws" % "aws-xray-recorder-sdk-apache-http" % awsXrayVersion
  // Instruments calls to AWS services made with AWS SDK for Java clients by adding a tracing client as a request handler.
  val libAwsXRaySdkAwsSdk: ModuleID = "com.amazonaws" % "aws-xray-recorder-sdk-aws-sdk" % awsXrayVersion
  // With aws-xray-recorder-sdk-aws-sdk, instruments all AWS SDK for Java clients automatically when added to the build
  val libAwsXRaySdkAwsInstrumentorSdk: ModuleID = "com.amazonaws" % "aws-xray-recorder-sdk-aws-sdk-instrumentor" % awsXrayVersion
  // Instruments outbound calls to a MySQL database made with JDBC.
  val libAwsXRayMySQLSdk: ModuleID = "com.amazonaws" % "aws-xray-recorder-sdk-sql-mysql" % awsXrayVersion
  // Instruments outbound calls to a PostgreSQL database made with JDBC.
  val libAwsXRayPostgresSdk: ModuleID = "com.amazonaws" % "aws-xray-recorder-sdk-sql-postgres" % awsXrayVersion
  //

  val libGuava: ModuleID = "com.google.guava" % "guava" % "23.0"
  val libAwsLambdaJavaCore: ModuleID = "com.amazonaws" % "aws-lambda-java-core" % "1.2.0"
  val libAwsLambdaJavaEvents: ModuleID = "com.amazonaws" % "aws-lambda-java-events" % "2.1.0"
  val libAwsEncryptionSDK: ModuleID = "com.amazonaws" % "aws-encryption-sdk-java" % "1.3.2"
  val libSecurityBouncyCastle: ModuleID = "org.bouncycastle" % "bcprov-ext-jdk15on" % "1.59"
  val libTypesafeConfig: ModuleID = "com.typesafe" % "config" % "1.3.3"
  val libPureConfig: ModuleID = "com.github.pureconfig" %% "pureconfig" % "0.9.1"
  val libScalaz: ModuleID = "org.scalaz" %% "scalaz-core" % "7.2.23"
  val libAvro4s: ModuleID = "com.github.dnvriend" %% "avro4s-core" % "1.8.3"
  val libAvro: ModuleID = "org.apache.avro" % "avro" % "1.8.2"
  val libAvroCompiler: ModuleID = "org.apache.avro" % "avro-compiler" % "1.8.2"
  val libPlayJson: ModuleID = "com.typesafe.play" %% "play-json" % "2.6.9"
  val libShapeless: ModuleID = "com.chuusai" %% "shapeless" % "2.3.2"
  val libSbtIO: ModuleID = "org.scala-sbt" %% "io" % "1.1.1"
  val libScalajHttp: ModuleID = "org.scalaj" %% "scalaj-http" % "2.4.0"
  val libAwsRequestSigner: ModuleID = "io.ticofab" %% "aws-request-signer" % "0.5.2"
  val libLogback: ModuleID = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val libScalaLogging: ModuleID = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
  val libJava8ScalaCompat: ModuleID = "org.scala-lang.modules" % "scala-java8-compat_2.12" % "0.9.0"

  val libAkkaActor: ModuleID = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val libAkkaStream: ModuleID = "com.typesafe.akka" %% "akka-stream" % akkaVersion

  // testing libs //
  val libScalaCheckTest: ModuleID = "org.scalacheck" %% "scalacheck" % "1.14.0"
  val libScalazScalaTest: ModuleID = "org.typelevel" %% "scalaz-scalatest" % "1.1.2"
  val libScalaTest: ModuleID = "org.scalatest" %% "scalatest" % "3.0.5"

  // sbt plugins //
  val libSbtAssembly: ModuleID = "com.eed3si9n" % "sbt-assembly" % "0.14.6"
}