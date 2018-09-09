# Serverless-Annotations

This project aims to do one thing - make the SLS framework ([www.serverless.com](www.serverless.com)) 
easier to use in the Java world.

## Bottom line

Annotated your classes, as seen below, specify 
some params in gradle, bada boom - you get a serverless.yml file out.

```kotlin
package org.densebrain.serverless.examples.simple
import com.amazonaws.services.lambda.runtime.*;
import org.densebrain.serverless.annotations.*
import org.densebrain.serverless.annotations.Function


data class TestRequestModel(val id:String)
data class TestResponseModel(val id:String)

@Function(
  name = "TestLambda",
  environment = [
    Environment(
      name = "REF_VAR",
      yaml = """"Fn::GetAtt": [SessionUpdateLambdaFunction, Arn]"""
    )
  ],
  http = [
    HttpEvent(
      path = "/test/{hello}",
      method = HttpEventMethod.GET,
      input = TestRequestModel::class,
      output = TestResponseModel::class,
      request = Request(
        paths = [Parameter("hello",true)],
        querystrings = [Parameter("since",false)]
      )
    )
  ],

  schedule = [
    ScheduleEvent("rate(3 minutes)")
  ],

  cloudwatch = [
    CloudwatchEvent("""
      event:
        source:
          - "aws.batch"
        detail:
          jobName:
            - "$""" + """{self:custom.customVar}"
    """)
  ]
)
class TestLambda : RequestHandler<TestRequestModel, TestResponseModel> {
  override fun handleRequest(input: TestRequestModel, context: Context): TestResponseModel {
    return TestResponseModel(input.id)
  }
}
```


## Gradle Configuration

```groovy
task serverlessBuild(type: ServerlessBuilderTask) {
  
  // WE SUGGEST USING SHADOW TO BUILD 
  // A SINGLE ARCHIVE TO MAKE SCANNING EASIER
  dependsOn(shadowJar)
  
  // PATH TO ARCHIVE OF CLASSES
  archive = file("${buildDir.absolutePath}/libs/${shadowJar.archiveName}")
  
  // (OPTIONAL) Bring your own serverless.yml template
  // If you specify a template then functions will be appended to 
  // your functions block
  serverlessConfigFile = serverlessBaseFile
  
  // OUTPUT PATH FOR serverless.yml
  outputFile = file("${projectDir.absolutePath}/serverless.yml")
  
  // BASE PACKAGE TO SCAN FOR ANNOTATED TYPES
  basePackage = "your.package.here"
  
  // GENERATE MODELS/DOCUMENTATION FOR SWAGGER, etc
  generateDocumentation = true
  
  // (OPTIONAL) If omitted then the documentation block is appended
  // to any existing "custom" block in the serverless.yml
  documentationOutputFile = file("${projectDir.absolutePath}/documentation.yml")
}

build.dependsOn(serverlessBuild)
```