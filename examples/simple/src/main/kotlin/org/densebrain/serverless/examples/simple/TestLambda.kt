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
      method = HttpMethod.GET,
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