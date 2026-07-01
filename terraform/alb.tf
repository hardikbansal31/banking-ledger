
# Flow: User/Vercel → ALB (port 80) → Target Group → ECS Task (port 8080)

# ─── The Load Balancer itself 
resource "aws_lb" "main" {
  name               = "${var.project_name}-alb"
  internal           = false                            # internet-facing
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id          # must span 2+ AZs

  tags = {
    Name = "${var.project_name}-alb"
  }
}

resource "aws_lb_target_group" "backend" {
  name        = "${var.project_name}-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"  # Fargate uses IP-based targets (not instance-based)

  # Health check — ALB pings this endpoint every 30s to confirm the app is alive.
  # If it fails 3 times, ALB stops sending traffic to that task.
  health_check {
    enabled             = true
    path                = "/api/v1/actuator/health"
    port                = "8080"
    protocol            = "HTTP"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 10
    interval            = 30
    matcher             = "200"
  }

  tags = {
    Name = "${var.project_name}-tg"
  }
}

# Listens on port 80 and forwards all incoming requests to the target group.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}
