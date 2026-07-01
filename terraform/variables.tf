# .env file basically

variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Name prefix for all resources (keeps naming consistent)"
  type        = string
  default     = "banking-ledger"
}


variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}


variable "db_username" {
  description = "Master username for the RDS MySQL instance"
  type        = string
  default     = "ledger_app"
}

variable "db_password" {
  description = "Master password for the RDS MySQL instance"
  type        = string
  sensitive   = true  # Terraform will never print this in logs
}

variable "db_name" {
  description = "Name of the MySQL database to create"
  type        = string
  default     = "banking_ledger"
}


variable "redis_password" {
  description = "AUTH password for ElastiCache Redis"
  type        = string
  sensitive   = true
}


variable "jwt_secret" {
  description = "Base64-encoded secret key for JWT signing (HS384)"
  type        = string
  sensitive   = true
}

variable "cors_allowed_origins" {
  description = "Comma-separated list of allowed CORS origins (your Vercel URL)"
  type        = string
  default     = "*"  # Update this to your Vercel URL after first deploy
}

variable "exchange_rate_api_key" {
  description = "API key for the exchange rate service"
  type        = string
  default     = "mock-key"
}
