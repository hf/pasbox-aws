export function handler(
  event: AWSLambda.CustomAuthorizerEvent
): Promise<AWSLambda.CustomAuthorizerResult> {
  return Promise.resolve({
    principalId: "user",
    policyDocument: {
      Version: "2012-10-17",
      Statement: [
        {
          Effect: "Allow",
          Action: "execute-api:Invoke",
          Resource: "*"
        }
      ]
    }
  });
}
