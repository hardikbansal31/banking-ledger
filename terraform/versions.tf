

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# The AWS provider reads credentials from ~/.aws/credentials
provider "aws" {
  region = var.aws_region
}
