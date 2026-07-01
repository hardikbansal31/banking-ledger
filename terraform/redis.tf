

# ─── ElastiCache Subnet Group ────────────────────────────────────────────────
resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project_name}-redis-subnet"
  subnet_ids = aws_subnet.private[*].id  # Private subnets — no internet access

  tags = {
    Name = "${var.project_name}-redis-subnet"
  }
}

# Using a replication group (even with 0 replicas) because it supports
# AUTH passwords and transit encryption — a plain "cluster" resource does not.
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${var.project_name}-redis"
  description          = "Redis for banking-ledger distributed locks and cache"

  engine               = "redis"
  engine_version       = "7.1"
  node_type            = "cache.t4g.micro"       # Free Tier eligible
  num_cache_clusters   = 1                        # Single node (no replicas — saves cost)
  port                 = 6379

  # Security
  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.redis.id]
  auth_token           = var.redis_password       # Matches REDIS_PASSWORD in Spring Boot
  transit_encryption_enabled = true               # Required when using auth_token

  # Cost savings
  automatic_failover_enabled = false              # Not needed with 1 node
  at_rest_encryption_enabled = true

  tags = {
    Name = "${var.project_name}-redis"
  }
}
