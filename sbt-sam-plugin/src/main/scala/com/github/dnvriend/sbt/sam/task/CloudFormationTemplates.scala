package com.github.dnvriend.sbt.sam.task

import com.github.dnvriend.sbt.aws.task.TemplateBody
import com.github.dnvriend.sbt.sam.task.Models.DynamoDb.TableWithIndex
import com.github.dnvriend.sbt.sam.task.Models.SNS.Topic
import com.github.dnvriend.sbt.sam.task.Models.{DynamoDb, Kinesis, Policies}
import play.api.libs.json._

import scalaz._
import scalaz.Scalaz._

object JsMonoids {
  val jsObjectMerge: Monoid[JsValue] = Monoid.instance({
    case (l, JsNull) => l
    case (JsNull, r) => r
    case (l: JsObject, r: JsObject) => l ++ r
    case (l, _) => l
  }, JsNull)
}

object CloudFormationTemplates {
  implicit val monoid: Monoid[JsObject] = Monoid.instance(_ ++ _, Json.obj())
  val templateFormatVersion: JsObject = Json.obj("AWSTemplateFormatVersion" -> "2010-09-09")
  val samTransform: JsObject = Json.obj("Transform" -> "AWS::Serverless-2016-10-31")

  /**
    * Returns the basic cloud formation template to create the stack and deployment bucket
    */
  def deploymentBucketTemplate(config: ProjectConfiguration): TemplateBody = {
    TemplateBody.fromJson(
      templateFormatVersion ++
        resources(bucketResource("SbtSamDeploymentBucket", config.samS3BucketName.value))
    )
  }

  def updateTemplate(config: ProjectConfiguration, jarName: String, latestVersion: String): TemplateBody = {
    TemplateBody.fromJson(
      templateFormatVersion ++
        samTransform ++
        resources(
          bucketResource("SbtSamDeploymentBucket", config.samS3BucketName.value),
          parseLambdaHandlers(config, config.samS3BucketName, jarName, latestVersion, config.lambdas),
          parseDynamoDBResource(config.tables, config.projectName, config.samStage),
          parseTopicResource(config),
          parseStreamResource(config),
          ServerlessApi.resource(config),
//          Cognito.UserPool.resource(config),
//          Cognito.UserPoolClient.resource(config),
//          parsePolicies(config.policies),
        )
        ++ outputs(config)
    )
  }

  /**
    * Merges a sequence of Outputs. Please note, CloudFormation templates support
    * a maximum of 60 outputs
    * see: http://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/outputs-section-structure.html
    */
  private def outputs(config: ProjectConfiguration): JsObject = {
    val outputs: List[JsValue] = List(
      outputServiceEndpoint(config),
    )
    Json.obj("Outputs" -> outputs.foldMap(identity)(JsMonoids.jsObjectMerge))
  }

  /**
    * Creates the output 'ServiceEndpoint' with a value eg.
    * 'https://gm3vkzgx9b.execute-api.eu-west-1.amazonaws.com/Prod'
    */
  private def outputServiceEndpoint(config: ProjectConfiguration): JsValue = {
    if (config.lambdas.exists(_.isInstanceOf[HttpHandler])) {
      Json.obj(
        "ServiceEndpoint" -> Json.obj(
          "Description" -> "URL of the service endpoint",
          "Value" -> Json.obj(
            "Fn::Join" -> Json.arr(
              "",
              Json.arr(
                "https://",
                ServerlessApi.logicalIdRestApi(config),
                ".execute-api.",
                PseudoParameters.ref(PseudoParameters.Region),
                ".",
                PseudoParameters.ref(PseudoParameters.URLSuffix),
                "/",
                ServerlessApi.logicalIdStage(config)
              )
            )
          )
        )
      )
    } else JsNull
  }

  /**
    * Merges a sequence of Resources
    */
  private def resources(resources: JsValue*): JsObject = {
    Json.obj("Resources" -> resources.toList.foldMap(identity)(JsMonoids.jsObjectMerge))
  }

  private def bucketResource(resourceName: String, bucketName: String): JsObject = {
    Json.obj(
      resourceName -> Json.obj(
        "Type" -> "AWS::S3::Bucket",
        "Properties" -> Json.obj(
          "AccessControl" -> "BucketOwnerFullControl",
          "BucketName" -> bucketName,
          "VersioningConfiguration" -> Json.obj("Status" -> "Enabled")
        )
      )
    )
  }

  private def parseLambdaHandlers(config: ProjectConfiguration, bucketName: SamS3BucketName, jarName: String, latestVersion: String, handlers: Set[LambdaHandler]): JsObject = {
    handlers.foldMap {
      case HttpHandler(lambdaConf, httpConf) ⇒
        parseLambdaHandler(
          config,
          bucketName,
          jarName,
          latestVersion,
          lambdaConf,
          apiGatewayEvent(lambdaConf.simpleClassName, httpConf)
        )
      case DynamoHandler(lambdaConf, dynamoConf) ⇒
        parseLambdaHandler(
          config,
          bucketName,
          jarName,
          latestVersion,
          lambdaConf,
          dynamoDbStreamEvent(lambdaConf.simpleClassName, dynamoConf)
        )
      case ScheduledEventHandler(lambdaConf, scheduleConf) ⇒
        parseLambdaHandler(
          config,
          bucketName,
          jarName,
          latestVersion,
          lambdaConf,
          scheduledEvent(lambdaConf.simpleClassName, scheduleConf)
        )
      case SNSEventHandler(lambdaConf, snsConf) ⇒
        parseLambdaHandler(
          config,
          bucketName,
          jarName,
          latestVersion,
          lambdaConf,
          snsEvent(lambdaConf.simpleClassName, snsConf, config.projectName, config.samStage.value)
        )
    }
  }

  private def parseLambdaHandler(cfg: ProjectConfiguration, samS3BucketName: SamS3BucketName, jarName: String, latestVersion: String, config: LambdaConfig, event: JsObject): JsObject = {
    Json.obj(
      config.simpleClassName → Json.obj(
        "Type" → "AWS::Serverless::Function",
        "Properties" → Json.obj(
          "Handler" → s"${config.fqcn}::handleRequest",
          "Runtime" → "java8",
          "CodeUri" → Json.obj(
            "Bucket" -> samS3BucketName.value,
            "Key" -> jarName,
            "Version" -> latestVersion
          ),
          "Policies" → Json.arr("AmazonDynamoDBFullAccess", "CloudWatchFullAccess", "CloudWatchLogsFullAccess", "AmazonSNSFullAccess", "AmazonKinesisFullAccess"),
          "Description" → config.description,
          "MemorySize" → config.memorySize,
          "Timeout" → config.timeout,
          "Tracing" → "Active",
          "Environment" -> Json.obj(
              "Variables" -> Json.obj(
              "STAGE" -> cfg.samStage.value,
              "PROJECT_NAME" -> cfg.projectName,
              "VERSION" -> cfg.projectVersion,
              "AWS_ACCOUNT_ID" -> CloudFormation.accountId
            )
          ),
          "Tags" -> Json.obj(
            "sbt:sam:projectName" -> cfg.projectName,
            "sbt:sam:projectVersion" -> cfg.projectVersion,
            "sbt:sam:stage" -> cfg.samStage.value
          ),
          "Events" → event
        )
      )
    )
  }

  private def snsEvent(eventName: String, conf: SNSConf, projectName: String, stageName: String): JsObject = {
    val prefix = s"$projectName-$stageName"
    val topicName: String = s"$prefix-${conf.topic}"
    val lambdaEventName = s"${eventName}Event"
    Json.obj(
      lambdaEventName -> Json.obj(
        "Type" -> "SNS",
        "Properties" -> Json.obj(
          "Topic" -> CloudFormation.snsArn(topicName)
        )
      )
    )
  }

  private def scheduledEvent(eventName: String, scheduleConf: ScheduleConf): JsObject = {
    Json.obj(
      eventName -> Json.obj(
        "Type" -> "Schedule",
        "Properties" -> Json.obj(
          "Schedule" -> scheduleConf.schedule
        )
      )
    )
  }

  private def apiGatewayEvent(eventName: String, httpConf: HttpConf): JsObject = {
    Json.obj(
      eventName -> Json.obj(
        "Type" -> "Api",
        "Properties" -> Json.obj(
          "Path" -> httpConf.path,
          "Method" -> httpConf.method,
          "RestApiId" -> Json.obj("Ref" -> "ServerlessRestApi")
        )
      ))
  }

  private def dynamoDbStreamEvent(eventName: String, dynamoConf: DynamoConf): JsObject = {
    Json.obj(
      eventName → Json.obj(
        "Type" → "DynamoDB",
        "Properties" → Json.obj(
          "Stream" → CloudFormation.getAtt(dynamoConf.tableName, "StreamArn"),
          "BatchSize" → dynamoConf.batchSize,
          "StartingPosition" → dynamoConf.startingPosition
        )
      ))
  }

  private def parseTopicResource(config: ProjectConfiguration): JsValue = {
    def mapTopicResource(topic: Topic): JsValue = {
      val prefix = s"${config.projectName}-${config.samStage.value}"
      val topicName: String = s"$prefix-${topic.name}"
      val displayName: String = s"$prefix-${topic.displayName}"
      Json.obj(
        topic.configName -> Json.obj(
          "Type" -> "AWS::SNS::Topic",
          "Properties" -> Json.obj(
            "DisplayName" -> displayName,
            "TopicName" -> topicName
          )
        )
      )
    }
    config.topics.foldMap(topic => mapTopicResource(topic))(JsMonoids.jsObjectMerge)
  }

  private def parseStreamResource(config: ProjectConfiguration): JsValue = {
    def mapStreamResource(stream: Kinesis.Stream): JsValue = {
      val streamName: String = s"${config.projectName}-${config.samStage.value}-${stream.name}"
      Json.obj(
        stream.configName -> Json.obj(
          "Type" -> "AWS::Kinesis::Stream",
          "Properties" -> Json.obj(
            "Name" -> streamName,
            "ShardCount" -> stream.shardCount,
            "RetentionPeriodHours" -> stream.retensionPeriodHours
          )
        )
      )
    }
    config.streams.foldMap(stream => mapStreamResource(stream))(JsMonoids.jsObjectMerge)
  }

  private def parseDynamoDBResource(tables: Set[DynamoDb.TableWithIndex], projectName: String, stage: SamStage): JsObject = {
    def streamJson(table: TableWithIndex) = table.stream match {
      case Some(s) ⇒ Json.obj(
        table.configName → Json.obj(
          "Properties" → Json.obj(
            "StreamSpecification" → Json.obj(
              "StreamViewType" → s
            )
          )
        )
      )
      case None ⇒ JsObject(Nil)
    }

    def indicesJson(table: TableWithIndex) = table.gsis match {
      case _ :: _ ⇒ Json.obj(
        table.configName → Json.obj(
          "Properties" → Json.obj(
            "GlobalSecondaryIndexes" → indexesToJson(table.gsis)
          )
        )
      )
      case Nil ⇒ JsObject(Nil)
    }

    tables.foldMap { table ⇒
      Json.obj(
        table.configName → Json.obj(
          "Type" → "AWS::DynamoDB::Table",
          "Properties" → Json.obj(
            "TableName" → s"$projectName-${stage.value}-${table.name}",
            "AttributeDefinitions" → attributeDefinitions(table),
            "KeySchema" → keySchemaToJson(table.hashKey, table.rangeKey),
            "ProvisionedThroughput" → Json.obj(
              "ReadCapacityUnits" → table.rcu,
              "WriteCapacityUnits" → table.wcu
            )
          )
        )
      ) deepMerge streamJson(table) deepMerge indicesJson(table)
    }
  }

  private def parsePolicies(policies: Set[Policies.Policy]): JsObject = {
    policies.foldMap { policy ⇒
      Json.obj(
        policy.configName → Json.obj(
          "Type" → "AWS::IAM::Policy",
          "DependsOn" → policy.dependsOn,
          "Properties" → Json.obj(
            "PolicyName" → policy.properties.name,
            "PolicyDocument" → Json.obj(
              "Version" → "2012-10-17",
              "Statement" → statementsToJson(policy.properties.statements)),
            "Roles" → rolesToJson(policy.properties.roles)
          )
        )
      )
    }
  }

  private def attributeDefinitions(table: DynamoDb.TableWithIndex): JsArray = {
    def toJson(name: String, `type`: String): JsObject = Json.obj(
      "AttributeName" → name,
      "AttributeType" → `type`
    )

    val indexKeys = table.gsis.map { index ⇒
      val indexHashKey = toJson(index.hashKey.name, index.hashKey.keyType)
      val rangeKey = index.rangeKey match {
        case Some(key) ⇒ toJson(key.name, key.keyType)
        case None ⇒ JsObject(Nil)
      }
      indexHashKey ++ rangeKey
    }

    val rangeKey = table.rangeKey match {
      case Some(key) ⇒ toJson(key.name, key.keyType)
      case None ⇒ JsObject(Nil)
    }

    val hashKey = toJson(table.hashKey.name, table.hashKey.keyType)
    val objects = (indexKeys :+ rangeKey :+ hashKey).filter(_.value != Map.empty)
    objects.foldLeft(Json.arr())((arr, a) ⇒ arr ++ Json.arr(a))
  }

  private def keySchemaToJson(hashKey: DynamoDb.HashKey, rangeKey: Option[DynamoDb.RangeKey]): JsArray = {
    val hashKeyJson = Json.obj(
      "AttributeName" → hashKey.name,
      "KeyType" → "HASH"
    )

    val rangeKeyJson = rangeKey match {
      case Some(rk) ⇒ Json.obj(
        "AttributeName" → rk.name,
        "KeyType" → "RANGE")
      case None ⇒ JsObject(Nil)
    }

    val list = List(hashKeyJson, rangeKeyJson).filter(_.value != Map.empty)
    list.foldLeft(Json.arr())((arr, a) ⇒ arr ++ Json.arr(a))
  }

  private def statementsToJson(statements: List[Policies.Statements]): JsArray = {
    statements.map { statement ⇒
      Json.obj(
        "Effect" → "Allow",
        "Action" → statement.allowedActions,
        "Resource" → statement.resource
      )
    }.foldLeft(Json.arr())((arr, a) ⇒ arr ++ Json.arr(a))
  }

  private def rolesToJson(roles: List[Policies.Role]): JsArray = {
    roles.map { role ⇒
      Json.obj("Ref" → role.ref)
    }.foldLeft(Json.arr())((arr, a) ⇒ arr ++ Json.arr(a))
  }

  private def indexesToJson(gsis: List[DynamoDb.GlobalSecondaryIndex]): JsArray = {
    val objects: List[JsObject] = gsis.map { index ⇒
      Json.obj(
        "IndexName" → index.indexName,
        "KeySchema" → keySchemaToJson(index.hashKey, index.rangeKey),
        "Projection" → Json.obj(
          "ProjectionType" → index.projectionType
        ),
        "ProvisionedThroughput" → Json.obj(
          "ReadCapacityUnits" → index.rcu,
          "WriteCapacityUnits" → index.wcu
        )
      )
    }
    objects.foldLeft(Json.arr())((arr, a) ⇒ arr ++ Json.arr(a))
  }
}

trait PseudoParameter
object PseudoParameters {
  /**
    * Returns the param
    */
  def param(param: PseudoParameter): String = {
    s"AWS::$param"
  }

  def ref(p: PseudoParameter): JsObject = {
    Json.obj("Ref" -> param(p))
  }

  def output(name: String, p: PseudoParameter): JsObject = {
    Json.obj(name -> ref(p))
  }

  /**
    * Returns a string representing the AWS Region in which the encompassing resource is being created, such as us-west-2.
    */
  case object Region extends PseudoParameter

  /**
    * Returns the AWS account ID of the account in which the stack is being created
    */
  case object AccountId extends PseudoParameter

  /**
    * Returns the list of notification Amazon Resource Names (ARNs) for the current stack.
    */
  case object NotificationArn extends PseudoParameter

  /**
    * Returns the partition that the resource is in. For standard AWS regions, the partition is aws.
    * For resources in other partitions, the partition is aws-partitionname. For example, the partition
    * for resources in the China (Beijing) region is aws-cn.
    */
  case object Partition extends PseudoParameter

  /**
    * Returns the name of the stack as specified with the aws cloudformation create-stack command, such as teststack.
    */
  case object StackName extends PseudoParameter

  /**
    * Returns the suffix for a domain. The suffix is typically amazonaws.com, but might differ by region.
    * For example, the suffix for the China (Beijing) region is amazonaws.com.cn.
    */
  case object URLSuffix extends PseudoParameter

}

object CloudFormation {
  def properties(props: JsValue*): JsObject = {
    Json.obj("Properties" -> props.toList.foldMap(identity)(JsMonoids.jsObjectMerge))
  }

  /**
    * The intrinsic function Ref returns the value of the specified parameter or resource.
    *
    * - When you specify a parameter's logical name, it returns the value of the parameter.
    * - When you specify a resource's logical name, it returns a value that you can typically
    * use to refer to that resource, such as a physical ID.
    *
    * see: http://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-ref.html
    */
  def ref(logicalName: String): JsObject = {
    Json.obj("Ref" -> logicalName)
  }

  /**
    * The 'Fn::GetAtt' intrinsic function returns the value of an attribute from a resource in the template.
    *
    * see: http://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/intrinsic-function-reference-getatt.html
    */
  def getAtt(logicalName: String, attributeName: String): JsObject = {
    Json.obj("Fn::GetAtt" -> Json.arr(logicalName, attributeName))
  }

  /**
    * Substitutes variables in an input string with their associated values at runtime.
    * Variables can be template parameter names, resource logical IDs or resource attributes.
    */
  def subst(input: String): JsObject = Json.obj("Fn::Sub" -> input)

  /**
    * Returns the AWS account id eg '123456789012'
    */
  def accountId: JsObject = subst("${AWS::AccountId}")

  /**
    * Returns the AWS region eg: 'us-east-2'
    */
  def region: JsObject = subst("${AWS::Region}")

  /**
    * Returns the stack id eg: 'arn:aws:cloudformation:us-east-1:123456789012:stack/MyStack/1c2fa620-982a-11e3-aff7-50e2416294e0'
    */
  def stackId: JsObject = subst("${AWS::StackId}")

  /**
    * Returns the stack name eg: 'MyStack'
    */
  def stackName: JsObject = subst("${AWS::StackName}")

  /**
    * Returns the arn of an sns topic by means of subst
    */
  def snsArn(topicName: String): JsObject = {
    // arn:aws:sns:eu-west-1:015242279314:sam-dynamodb-seed-dnvriend-person-received
    val snsInput = "arn:aws:sns:${AWS::Region}:${AWS::AccountId}:" + topicName
    subst(snsInput)
  }
}

object Cognito {

  object UserPool {
    def logicalResourceId(config: ProjectConfiguration): String = {
      "ServerlessUserPool"
    }

    /**
      * Returns a CloudFormation configuration based on the ProjectConfiguration
      */
    def resource(config: ProjectConfiguration): JsObject = {
      Json.obj(
        logicalResourceId(config) -> (Json.obj(
          "Type" -> "AWS::Cognito::UserPool"
        ) ++ CloudFormation.properties(
          propUserPoolName(config),
          propAdminCreateUserConfig(config),
          propPolicies(config),
        ))
      )
    }

    /**
      * A string used to name the user pool.
      */
    def propUserPoolName(config: ProjectConfiguration): JsValue = {
      Json.obj("UserPoolName" -> "auth_pool")
    }

    /**
      * The type of configuration for creating a new user profile.
      */
    def propAdminCreateUserConfig(config: ProjectConfiguration): JsValue = {
      Json.obj("AdminCreateUserConfig" -> Json.obj(
        "AllowAdminCreateUserOnly" -> true,
        "UnusedAccountValidityDays" -> 30
      ))
    }

    /**
      * The policies associated with the Amazon Cognito user pool.
      */
    def propPolicies(config: ProjectConfiguration): JsValue = {
      Json.obj(
        "Policies" -> Json.obj(
          "PasswordPolicy" -> Json.obj(
            "MinimumLength" -> 6,
            "RequireLowercase" -> true,
            "RequireNumbers" -> false,
            "RequireSymbols" -> false,
            "RequireUppercase" -> false
          )
        )
      )
    }

    /**
      * When the logical ID of this resource is provided to the Ref intrinsic function, Ref returns a generated ID,
      * such as 'us-east-2_zgaEXAMPLE'
      */
    def logicalId(config: ProjectConfiguration): JsValue = {
      CloudFormation.ref(logicalResourceId(config))
    }

    /**
      * The provider name of the Amazon Cognito user pool, specified as a String.
      */
    def providerName(config: ProjectConfiguration): JsValue = {
      CloudFormation.getAtt(logicalResourceId(config), "ProviderName")
    }

    /**
      * The URL of the provider of the Amazon Cognito user pool, specified as a String.
      */
    def providerUrl(config: ProjectConfiguration): JsValue = {
      CloudFormation.getAtt(logicalResourceId(config), "ProviderURL")
    }

    /**
      * The Amazon Resource Name (ARN) of the user pool, such as
      * 'arn:aws:cognito-idp:us-east-2:123412341234:userpool/us-east-1 _123412341'
      */
    def arn(config: ProjectConfiguration): JsValue = {
      CloudFormation.getAtt(logicalResourceId(config), "Arn")
    }
  }

  /**
    * creates an Amazon Cognito user pool client.
    */
  object UserPoolClient {
    def logicalResourceId(config: ProjectConfiguration): String = {
      "ServerlessUserPoolClient"
    }

    def resource(config: ProjectConfiguration): JsObject = {
      Json.obj(
        logicalResourceId(config) -> (Json.obj(
          "Type" -> "AWS::Cognito::UserPoolClient",
          "DependsOn" -> Cognito.UserPool.logicalResourceId(config),
        ) ++ CloudFormation.properties(
          propClientName(config),
          propExplicitAuthFlows(config),
          propUserPoolId(config),
        ))
      )
    }

    /**
      * The client name for the user pool client that you want to create.
      */
    def propClientName(config: ProjectConfiguration): JsObject = {
      val clientName = "client" //todo: set the client name
      Json.obj("ClientName" -> clientName)
    }

    /**
      * The explicit authentication flows, which can be one of the following:
      * - ADMIN_NO_SRP_AUTH
      * - CUSTOM_AUTH_FLOW_ONLY.
      */
    def propExplicitAuthFlows(config: ProjectConfiguration): JsObject = {
      Json.obj("ExplicitAuthFlows" -> Json.arr("ADMIN_NO_SRP_AUTH"))
    }

    /**
      * The user pool ID for the user pool where you want to create a client.
      */
    def propUserPoolId(config: ProjectConfiguration): JsObject = {
      Json.obj("UserPoolId" -> Cognito.UserPool.logicalId(config))
    }
  }
}

object ServerlessApi {
  /**
    * Returns the logical resource id
    */
  def logicalResourceId(config: ProjectConfiguration): String = {
    "ServerlessRestApi"
  }

  /**
    * Returns the logicalId of 'AWS::ApiGateway::RestApi'
    */
  def logicalIdRestApi(config: ProjectConfiguration): JsObject = {
    CloudFormation.ref(logicalResourceId(config))
  }

  /**
    * Returns the logicalId of 'AWS::ApiGateway::Stage'
    */
  def logicalIdStage(config: ProjectConfiguration): JsObject = {
    val logicalName = s"${logicalResourceId(config)}${config.samStage.value}Stage"
    CloudFormation.ref(logicalName)
  }

  private def properties(props: JsValue*): JsObject = {
    Json.obj("Properties" -> props.toList.foldMap(identity)(JsMonoids.jsObjectMerge))
  }

  def resource(config: ProjectConfiguration): JsValue = {
    if (!config.lambdas.exists(_.isInstanceOf[HttpHandler])) JsNull else {
      Json.obj(
        logicalResourceId(config) -> (Json.obj(
          "Type" -> "AWS::Serverless::Api",
//          "DependsOn" -> Cognito.UserPool.logicalResourceId(config),
        ) ++ properties(
          propStageName(config),
          propDefinitionBody(config)
        ))
      )
    }
  }

  def propStageName(config: ProjectConfiguration): JsValue = {
    Json.obj("StageName" -> config.samStage.value)
  }

  def propDefinitionBody(config: ProjectConfiguration): JsValue = {
    Json.obj("DefinitionBody" -> Swagger.spec(config))
  }

  def definitionBodyElements(xs: JsValue*): JsValue = {
    xs.toList.foldMap(identity)(JsMonoids.jsObjectMerge)
  }
}

/**
  * see: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md
  * see: http://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions.html
  */
object Swagger {
  private def merge(parts: JsValue*): JsValue = {
    parts.toList.foldMap(identity)(JsMonoids.jsObjectMerge)
  }
  def spec(config: ProjectConfiguration): JsValue = {
    merge(
      Parts.swaggerVersion,
      Parts.info(config),
      Parts.paths(config),
//      Parts.securityDefinitions(config),
    )
  }

  object Parts {
    /**
      * Required: Specifies the Swagger Specification version being used. The value must be '2.0'
      */
    val swaggerVersion: JsValue = Json.obj("swagger" -> "2.0")

    /**
      * Required: Provides metadata about the API. The metadata can be used by the clients if needed.
      */
    def info(config: ProjectConfiguration): JsValue = {
      val title = s"${config.projectName}-${config.samStage.value}"
      Json.obj(
      "info" -> Json.obj(
        "version" -> "2017-02-24T04:09:00Z",
        "title" -> title
      )
    )
  }

    /**
      * Required: The available paths and operations for the API.
      */
    def paths(config: ProjectConfiguration): JsValue = {
      val httpHandlers: Set[HttpHandler] = config.lambdas.collect {
        case h: HttpHandler => h
      }
      val handersByPath: Map[String, Set[HttpHandler]] = httpHandlers.groupBy(_.httpConf.path)
      val pathsWithOperations = handersByPath.map { case (resourcePath, handlers) => path(config, resourcePath, handlers) }.toList
      Json.obj("paths" -> pathsWithOperations.foldMap(identity)(JsMonoids.jsObjectMerge))
    }

    /**
      * A relative path to an individual endpoint. The field name MUST begin with a slash.
      * The path is appended to the basePath in order to construct the full URL.
      */
    def path(config: ProjectConfiguration, path: String, handlersForPath: Set[HttpHandler]): JsValue = {
      val operations = handlersForPath.map(handler => operation(config, handler)).toList
      Json.obj(path -> merge(operations:_*))
    }

    /**
      * add swagger operation and AWS ApiGateway extensions
      */
    def operation(config: ProjectConfiguration, handler: HttpHandler) = {
      val method: String = handler.httpConf.method
      Json.obj(method -> merge(
          AmazonApiGatewayIntegration.swaggerExtension(config, handler),
          responses,
//          security,
        )
      )
    }

    /**
      * Security scheme definitions that can be used across the specification.
      * http://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-integrate-with-cognito.html
      * http://docs.aws.amazon.com/apigateway/latest/developerguide/apigateway-enable-cognito-user-pool.html
      * https://github.com/dnvriend/serverless-test/blob/master/05-person-repository/auth.txt
      * https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#security-definitions-object
      * http://docs.aws.amazon.com/apigateway/latest/developerguide/api-gateway-swagger-extensions-authorizer.html
      */
    def securityDefinitions(config: ProjectConfiguration): JsValue = {
      Json.obj(
        "securityDefinitions" -> Json.obj(
          "auth_pool" -> Json.obj(
            "type" -> "apiKey",
            "name" -> "Authorization",
            "in" -> "header",
            "x-amazon-apigateway-authtype" -> "cognito_user_pools",
            "x-amazon-apigateway-authorizer" -> Json.obj(
              "type" -> "cognito_user_pools",
              "providerARNs" -> Json.arr(
                "arn:aws:cognito-idp:eu-west-1:015242279314:userpool/eu-west-1_V0UvYaYoz" //todo: replace hardcoded cognito arn with the one configured in the project
              ),
            )
          )
        )
      )
    }

    /**
      * A list of MIME types the APIs can consume.
      */
    val consumes = Json.obj("consumes" -> Json.arr("application/json"))
    /**
      * A list of MIME types the APIs can produce.
      */
    val produces = Json.obj("produces" -> Json.arr("application/json"))

    /**
      * An object to hold responses that can be used across operations.
      */
    val responses = Json.obj("responses" -> Json.obj())

    /**
      * A declaration of which security schemes are applied for the API as a whole.
      */
    val security = Json.obj("security" -> Json.arr(Json.obj("auth_pool" -> Json.arr())))
  } // end parts

  /**
    * Specifies details of the backend integration used for this method.
    * The result is an API Gateway integration object. In this case, it
    * uses an AWS Lambda function as the backend for the request.
    */
  object AmazonApiGatewayIntegration {
    def swaggerExtension(config: ProjectConfiguration, http: HttpHandler): JsValue = {
      Json.obj(
        "x-amazon-apigateway-integration" -> merge(
          uri(http),
          passthroughBehavior,
          httpMethod,
          integrationType,
        )
      )
    }

    /**
      * The HTTP method used in the integration request. For Lambda function invocations, the value must be POST.
      */
    val httpMethod: JsValue = Json.obj("httpMethod" -> "POST")
    /**
      * HTTP, HTTP_PROXY, AWS, AWS_PROXY
      * see: https://docs.aws.amazon.com/apigateway/api-reference/resource/integration/
      */
    val integrationType: JsValue = Json.obj("type" -> "aws_proxy")
    val passthroughBehavior: JsValue = Json.obj("passthroughBehavior" -> "when_no_match")

    def uri(http: HttpHandler): JsValue = {
      val lambdaUri = "arn:aws:apigateway:${AWS::Region}:lambda:path/2015-03-31/functions/${" + http.lambdaConfig.simpleClassName + ".Arn}/invocations"
      Json.obj("uri" -> Json.obj("Fn::Sub" -> lambdaUri))
    }
  }
}