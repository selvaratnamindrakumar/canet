output "rds_endpoint" {
  description = "RDS instance hostname (without port)"
  value       = aws_db_instance.postgres.address
}

output "rds_port" {
  description = "RDS instance port"
  value       = aws_db_instance.postgres.port
}

output "api_gateway_url" {
  description = "Invoke URL for the API Gateway HTTP API default stage"
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "cloudfront_domain" {
  description = "CloudFront distribution domain name for the frontend"
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "s3_bucket_name" {
  description = "Name of the S3 bucket hosting the frontend static files"
  value       = aws_s3_bucket.frontend.bucket
}

output "lambda_function_name" {
  description = "Name of the Lambda function running the FastAPI backend"
  value       = aws_lambda_function.api.function_name
}

output "secrets_manager_arn" {
  description = "ARN of the Secrets Manager secret that holds the DB credentials"
  value       = aws_secretsmanager_secret.db_credentials.arn
}

output "cloudfront_distribution_id" {
  description = "CloudFront distribution ID (needed for cache invalidations)"
  value       = aws_cloudfront_distribution.frontend.id
}
