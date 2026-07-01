
resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id  # Private subnets — no internet access

  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

# ─── RDS Instance ────────────────────────────────────────────────────────────
resource "aws_db_instance" "mysql" {
  identifier     = "${var.project_name}-mysql"
  engine         = "mysql"
  engine_version = "8.0"                        # AWS RDS supports 8.0, close to your 8.4
  instance_class = "db.t3.micro"                # Free Tier eligible

  allocated_storage     = 20                    # 20 GB (Free Tier allows up to 20 GB)
  max_allocated_storage = 20                    # Disable auto-scaling to prevent surprise costs
  storage_type          = "gp2"                 # General Purpose SSD

  db_name  = var.db_name                        # "banking_ledger"
  username = var.db_username                    # "ledger_app"
  password = var.db_password                    # From terraform.tfvars (sensitive)

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  # ── Cost-saving options for a portfolio project ──
  multi_az            = false                   # Single AZ (Multi-AZ doubles cost)
  publicly_accessible = false                   # No public IP — only ECS can reach it
  skip_final_snapshot = true                    # Don't create a snapshot on destroy (saves time + cost)

  # ── Connection settings ──
  port = 3306

  # ── Parameters matching your application.yml ──
  parameter_group_name = "default.mysql8.0"

  tags = {
    Name = "${var.project_name}-mysql"
  }
}
