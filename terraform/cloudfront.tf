# =============================================================================
# CloudFront CDN — Free HTTPS termination for the ALB
# =============================================================================
# Because Vercel forces HTTPS, browser security blocks HTTP requests to the ALB.
# CloudFront provides a free secure domain (https://xxx.cloudfront.net) pointing
# to our HTTP ALB, solving the Mixed Content issue for free.

resource "aws_cloudfront_distribution" "api" {
  origin {
    domain_name = aws_lb.main.dns_name
    origin_id   = "ALB"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only" # CloudFront connects to ALB via HTTP
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  enabled         = true
  is_ipv6_enabled = true
  comment         = "HTTPS proxy for ECS ALB"

  # We configure it to NOT cache anything, since this is a dynamic API
  default_cache_behavior {
    allowed_methods  = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "ALB"

    # Forward all headers (needed for CORS and Spring Security to work correctly)
    forwarded_values {
      query_string = true
      headers      = ["*"] 

      cookies {
        forward = "all"
      }
    }

    viewer_protocol_policy = "redirect-to-https" # Redirect user http requests to https
    min_ttl                = 0
    default_ttl            = 0
    max_ttl                = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # Use the default CloudFront SSL certificate (*.cloudfront.net)
  viewer_certificate {
    cloudfront_default_certificate = true
  }

  tags = {
    Name = "${var.project_name}-cloudfront"
  }
}
