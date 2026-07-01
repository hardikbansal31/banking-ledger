
# Flow: You push a Docker image to ECR → ECS pulls it → Fargate runs it.

# ─── Get current AWS account ID (needed for ECR repository URL) ──────────────
data "aws_caller_identity" "current" {}

resource "aws_ecr_repository" "backend" {
  name                 = "${var.project_name}-backend"
  image_tag_mutability = "MUTABLE"    # Allows overwriting :latest tag
  force_delete         = true         # Allow terraform destroy even if images exist

  image_scanning_configuration {
    scan_on_push = false              # Skip vulnerability scanning to save time
  }

  tags = {
    Name = "${var.project_name}-ecr"
  }
}

# ─── ECS Cluster ──────────────────────────────────────────────────────────────
# A logical grouping of tasks/services. Think of it as the "project folder"
# that holds your running containers.
resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"

  tags = {
    Name = "${var.project_name}-cluster"
  }
}

# ─── IAM Role: Task Execution Role ───────────────────────────────────────────
# This role lets ECS itself (not your app) perform infrastructure actions:
#   - Pull Docker images from ECR
#   - Write container logs to CloudWatch
# Think of it as the "backstage crew" permissions.
resource "aws_iam_role" "ecs_execution" {
  name = "${var.project_name}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${var.project_name}-ecs-execution-role"
  }
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# ─── CloudWatch Log Group ────────────────────────────────────────────────────
# Container stdout/stderr is sent here. You can view logs in the AWS Console
# under CloudWatch → Log groups → /ecs/banking-ledger
resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/${var.project_name}"
  retention_in_days = 7     # Keep logs for 7 days only (saves storage costs)

  tags = {
    Name = "${var.project_name}-logs"
  }
}

# ─── ECS Task Definition ─────────────────────────────────────────────────────
# This is the "blueprint" for your container. It defines:
#   - Which Docker image to run
#   - How much CPU/RAM to allocate
#   - What environment variables to inject (your .env equivalent)
#   - Where to send logs
resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.project_name}-task"
  network_mode             = "awsvpc"          # Required for Fargate
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"             # 0.25 vCPU (minimum Fargate)
  memory                   = "512"             # 0.5 GB RAM (minimum Fargate)
  execution_role_arn       = aws_iam_role.ecs_execution.arn

  container_definitions = jsonencode([
    {
      name      = "${var.project_name}-container"
      image     = "${aws_ecr_repository.backend.repository_url}:latest"
      essential = true

      portMappings = [
        {
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
        }
      ]

      # ════════════════════════════════════════════════════════════════════════
      # Environment Variables — this is your .env file equivalent on AWS
      # ════════════════════════════════════════════════════════════════════════
      # These map 1:1 to what application.yml reads via ${VAR_NAME:default}
      environment = [
        # ── Spring Profile ──
        { name = "APP_ENVIRONMENT", value = "production" },
        { name = "SPRING_PROFILES_ACTIVE", value = "production" },

        # ── MySQL (RDS) ──
        # aws_db_instance.mysql.address = the RDS hostname Terraform creates
        { name = "MYSQL_HOST", value = aws_db_instance.mysql.address },
        { name = "MYSQL_HOST_PORT", value = "3306" },
        { name = "MYSQL_DATABASE", value = var.db_name },
        { name = "MYSQL_USER", value = var.db_username },
        { name = "MYSQL_PASSWORD", value = var.db_password },

        # ── Redis (ElastiCache) ──
        # primary_endpoint_address = the Redis hostname Terraform creates
        { name = "REDIS_HOST", value = aws_elasticache_replication_group.redis.primary_endpoint_address },
        { name = "REDIS_HOST_PORT", value = "6379" },
        { name = "REDIS_PASSWORD", value = var.redis_password },

        # ── JWT ──
        { name = "JWT_SECRET", value = var.jwt_secret },

        # ── CORS ──
        { name = "CORS_ALLOWED_ORIGINS", value = var.cors_allowed_origins },

        # ── Exchange Rate ──
        { name = "EXCHANGE_RATE_API_KEY", value = var.exchange_rate_api_key },

        # ── Override ddl-auto to 'update' for initial schema creation ──
        # Your production profile sets 'validate', but since there's no
        # migration tool (Flyway/Liquibase), we use 'update' so Hibernate
        # creates the tables on first boot. Change to 'validate' later
        # once the schema is stable.
        { name = "SPRING_JPA_HIBERNATE_DDL_AUTO", value = "update" },

        # ── Logging ──
        { name = "LOG_LEVEL", value = "INFO" },

        # ── Redisson needs to know about TLS for ElastiCache ──
        { name = "SPRING_DATA_REDIS_SSL_ENABLED", value = "true" },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.ecs.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }
    }
  ])

  tags = {
    Name = "${var.project_name}-task"
  }
}

# ─── ECS Service ──────────────────────────────────────────────────────────────
# The Service ensures your task is always running. If the container crashes,
# ECS automatically starts a new one. It also registers tasks with the ALB
# target group so traffic flows to them.
resource "aws_ecs_service" "backend" {
  name            = "${var.project_name}-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = 1                    # Run 1 instance (scale up later if needed)
  launch_type     = "FARGATE"
  health_check_grace_period_seconds = 180

  network_configuration {
    subnets          = aws_subnet.public[*].id        # Public subnets (no NAT needed)
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true                            # Needed for ECR image pull without NAT
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "${var.project_name}-container"
    container_port   = 8080
  }

  # Wait for the ALB listener to be created before starting the service
  depends_on = [aws_lb_listener.http]

  tags = {
    Name = "${var.project_name}-service"
  }
}
