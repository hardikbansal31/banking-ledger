
output "cloudfront_domain_name" {
  description = "Secure CloudFront URL (set this in Vercel as VITE_API_BASE_URL)"
  value       = "https://${aws_cloudfront_distribution.api.domain_name}"
}

output "ecr_repository_url" {
  description = "ECR repository URL — push your Docker image here"
  value       = aws_ecr_repository.backend.repository_url
}

output "rds_endpoint" {
  description = "RDS MySQL endpoint (hostname:port)"
  value       = "${aws_db_instance.mysql.address}:${aws_db_instance.mysql.port}"
}

output "redis_endpoint" {
  description = "ElastiCache Redis primary endpoint"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "ecs_cluster_name" {
  description = "ECS cluster name (for aws ecs update-service commands)"
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service name (for aws ecs update-service commands)"
  value       = aws_ecs_service.backend.name
}

# ─── Quick-reference commands printed after apply ─────────────────────────────

output "next_steps" {
  description = "Commands to run after terraform apply"
  value       = <<-EOT

    ╔══════════════════════════════════════════════════════════════════╗
    ║                    NEXT STEPS                                   ║
    ╠══════════════════════════════════════════════════════════════════╣
    ║                                                                 ║
    ║  1. Build & push your Docker image:                             ║
    ║                                                                 ║
    ║     aws ecr get-login-password --region ${var.aws_region} |     ║
    ║       docker login --username AWS --password-stdin               ║
    ║       ${aws_ecr_repository.backend.repository_url}              ║
    ║                                                                 ║
    ║     docker build -t banking-ledger-backend .                    ║
    ║                                                                 ║
    ║     docker tag banking-ledger-backend:latest                    ║
    ║       ${aws_ecr_repository.backend.repository_url}:latest       ║
    ║                                                                 ║
    ║     docker push                                                 ║
    ║       ${aws_ecr_repository.backend.repository_url}:latest       ║
    ║                                                                 ║
    ║  2. Force ECS to pull the new image:                            ║
    ║                                                                 ║
    ║     aws ecs update-service                                      ║
    ║       --cluster ${aws_ecs_cluster.main.name}                    ║
    ║       --service ${aws_ecs_service.backend.name}                 ║
    ║       --force-new-deployment                                    ║
    ║                                                                 ║
    ║  3. Set VITE_API_BASE_URL in Vercel to:                         ║
    ║     https://${aws_cloudfront_distribution.api.domain_name}      ║
    ║                                                                 ║
    ╚══════════════════════════════════════════════════════════════════╝

  EOT
}
