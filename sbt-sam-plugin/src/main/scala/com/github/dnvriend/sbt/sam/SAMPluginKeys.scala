// Copyright 2017 Dennis Vriend
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.github.dnvriend.sbt.sam

import com.amazonaws.services.cloudformation.model.Stack
import com.github.dnvriend.sbt.aws.task._
import com.github.dnvriend.sbt.sam.resource.bucket.model.S3Bucket
import com.github.dnvriend.sbt.sam.resource.cognito.model.{ Authpool, ImportAuthPool }
import com.github.dnvriend.sbt.sam.resource.dynamodb.model._
import com.github.dnvriend.sbt.sam.resource.firehose.s3.model.S3Firehose
import com.github.dnvriend.sbt.sam.resource.kinesis.model._
import com.github.dnvriend.sbt.sam.cf.rds.RDSInstance
import com.github.dnvriend.sbt.sam.resource.authorizer.AuthorizerType
import com.github.dnvriend.sbt.sam.resource.role.model.IamRole
import com.github.dnvriend.sbt.sam.resource.sns.model._
import com.github.dnvriend.sbt.sam.task.ClassifySqlFiles.SqlApplication
import com.github.dnvriend.sbt.sam.task.{ LambdaHandler, ProjectClass, ProjectConfiguration, ProjectLambda }
import sbt._

object SAMPluginKeys {
  // sam settings
  lazy val samStage = settingKey[String]("The stage to deploy the service to")
  lazy val samStageValue = SettingKey[String]("samStageValue") // The actual stage value
  lazy val samS3BucketName = SettingKey[String]("samS3BucketName") // The S3 deployment bucket name for the sam project
  lazy val samCFTemplateName = SettingKey[String]("samCFTemplateName") // The cloudformation template name for the sam project

  // sam worker tasks
  lazy val samProjectClassLoader = TaskKey[ClassLoader]("samProjectClassLoader") // sam's project classloader
  lazy val discoveredClassFiles = TaskKey[Set[File]]("discoveredClassFiles") // returns a set of discovered class files
  lazy val discoveredClasses = TaskKey[Set[ProjectClass]]("discoveredClasses") // returns a set of discovered classes
  lazy val discoveredLambdas = TaskKey[Set[ProjectLambda]]("discoveredLambdas") // returns a set of discovered unclassified lambdas
  lazy val classifiedLambdas = TaskKey[Set[LambdaHandler]]("classifiedLambdas") // returns a set of classified lambdas
  lazy val discoveredSqlFiles = TaskKey[Set[File]]("discoveredSqlFiles") //TODO: not yet necessary but for future use for analytical engines with sql frontends
  lazy val classifiedSqlFiles = taskKey[List[SqlApplication]]("classifiedSqlFiles") // returns a set of classified sql files
  lazy val discoveredResources = TaskKey[Set[Class[_]]]("discoveredResources") // Returns a set of discovered aws resources
  lazy val samProjectConfiguration = taskKey[ProjectConfiguration]("samProjectConfiguration") // The sam project configuration
  lazy val samUploadArtifact = TaskKey[PutObjectResponse]("samUploadArtifact") // Upload deployment artifact to the S3 deployment bucket
  lazy val samDeleteArtifact = taskKey[Unit]("samDeleteArtifact") // Delete deployment artifact from the S3 deployment bucket
  lazy val samDeleteCloudFormationStack = TaskKey[Unit]("samDeleteCloudFormationStack") // Deletes the cloud formation stack
  lazy val samCreateCloudFormationStack = TaskKey[Unit]("samCreateCloudFormationStack") // Create the cloud formation stack
  lazy val samUpdateCloudFormationStack = TaskKey[Unit]("samUpdateCloudFormationStack") // Update the cloud formation stack
  lazy val samDescribeCloudFormationStackForCreate = TaskKey[Option[Stack]]("samDescribeCloudFormationStackForCreate") // Determine the state of the cloud for create
  lazy val samDescribeCloudFormationStack = TaskKey[Option[Stack]]("samDescribeCloudFormationStack") // Determine the state of the cloud for update or delete
  lazy val samServiceEndpoint = TaskKey[Option[ServiceEndpoint]]("samServiceEndpoint") // Shows the service endpoint
  lazy val samCreateUsers = taskKey[Unit]("samCreateUsers") // Deploys and authenticates cognito users
  lazy val samCreateUserToken = taskKey[Unit]("samCreateUserToken") // Gets the ID token for a user by username and password
  lazy val samCheckDeploymentBucketExists = taskKey[Unit]("samCheckDeploymentBucketExists") // checks whether the deployment bucket exists

  // resource tasks
  lazy val dynamoDbTableResources = taskKey[Set[TableWithIndex]]("Retrieves a set of tables, which are configured in the Lightbend Config.")
  lazy val iamRolesResources = taskKey[Set[IamRole]]("Retrieves a set of iam roles, which are configured in the Lightbend Config.")
  lazy val topicResources = taskKey[Set[Topic]]("Retrieves a set of topics, which are configured in the Lightbend Config.")
  lazy val streamResources = taskKey[Set[KinesisStream]]("Retrieves a set of streams, which are configured in the Lightbend Config.")
  lazy val cognitoResources = taskKey[Option[Authpool]]("Tries to retrieve a cognito configuration, specified in the Lightbend Config .")
  lazy val bucketResources = taskKey[Set[S3Bucket]]("Retrieves a set of s3 buckets, which are configured in the Lightbend Config.")
  lazy val s3FirehoseResources = taskKey[Set[S3Firehose]]("Retrieves a set of s3 firehose data delivery streams, which are configured in the Lightbend Config.")
  lazy val rdsResources = taskKey[Set[RDSInstance]]("Retrieves a set of RDS instances, which are configured in the Lightbend Config.")
  lazy val importAuthpool = taskKey[Option[ImportAuthPool]]("Tries to retrieve a Cognito Authpool that can be imported from CloudFormation. ")
  lazy val authorizerType = taskKey[AuthorizerType]("Tries to retrieve an AuthorizerType that will be used as the authorizer for API gateway.")

  // sam tasks
  lazy val samInfo = taskKey[Unit]("Show info the service")
  lazy val samDeploy = taskKey[Unit]("Deploy a service")
  lazy val samRemove = taskKey[Unit]("Remove a service")
  lazy val samUpdate = inputKey[Unit]("Update a service")
  lazy val samValidate = taskKey[Unit]("Validates the cloud formation template")
  lazy val samLogs = inputKey[Unit]("Shows logs of the specified lambda")
}
