variable "aws_region" {
  description = "AWS region to deploy resources into"
  type        = string
  default     = "eu-west-1"
}

variable "project_name" {
  description = "Short name used as a prefix for all resource names"
  type        = string
  default     = "skills-app"
}

variable "environment" {
  description = "Deployment environment (dev | staging | prod)"
  type        = string
  default     = "dev"
}

variable "db_name" {
  description = "Name of the PostgreSQL database to create on RDS"
  type        = string
  default     = "skills_db"
}

variable "db_username" {
  description = "Master username for the RDS PostgreSQL instance"
  type        = string
  default     = "skillsadmin"
}

variable "db_password" {
  description = "Master password for the RDS PostgreSQL instance (stored in Secrets Manager)"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance type"
  type        = string
  default     = "db.t3.micro"
}

variable "lambda_memory_mb" {
  description = "Memory allocated to the Lambda function in megabytes"
  type        = number
  default     = 512
}
