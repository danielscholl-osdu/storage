# OSDU SPI Fork Management Template

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![GitHub Issues](https://img.shields.io/github/issues/danielscholl-osdu/osdu-fork-template)](https://github.com/danielscholl-osdu/osdu-fork-template/issues)
[![Documentation](https://img.shields.io/badge/Documentation-Live-blue?logo=gitbook)](https://danielscholl-osdu.github.io/osdu-fork-template/)

> **Automated management for long-lived forks with AI-enhanced workflows**

## What This Template Provides

- 🔄 Automated upstream synchronization with conflict detection
- 🤖 AI-enhanced PR descriptions and conflict guidance
- 🛡️ Three-branch safety strategy
- 📈 Release correlation tracking
- 🎯 Zero-maintenance operations

**Perfect for**: OSDU teams needing Azure SPI customizations while staying current with upstream.

## Quick Start

### 1. Create Your Fork Repository

Click **"Use this template"** → Choose repository name → **Create repository**

### 2. Initialize Your Fork

1. Go to **Actions** → **"Repository Initialization"** → **"Run workflow"**
2. Follow the setup instructions in the automatically created issue
3. Provide your upstream repository URL when prompted
4. Wait 2-5 minutes for complete setup

### 3. Configure Secrets (Optional but Recommended)

For full automation, add these secrets in **Settings** → **Secrets and variables** → **Actions**:

| Secret | Purpose | Required |
|--------|---------|----------|
| `GH_TOKEN` | Repository automation | For branch protection & full automation |
| `ANTHROPIC_API_KEY` | AI-enhanced PR descriptions | Optional but recommended |

### 4. Start Using

- **Daily sync** happens automatically at midnight UTC
- **Manual sync** available in Actions → "Upstream Synchronization"  
- **Conflicts** create detailed issues with resolution guidance
- **Releases** are automatically versioned and correlated with upstream

## Support and Contributing

📚 **[Complete Documentation](https://danielscholl-osdu.github.io/osdu-fork-template/)** - Comprehensive guides covering system concepts, architecture, workflows, and AI integration.

- **Issues**: [Report bugs or request features](https://github.com/danielscholl-osdu/osdu-fork-template/issues)
- **Discussions**: [Community support and questions](https://github.com/danielscholl-osdu/osdu-fork-template/discussions)
- **Contributing**: See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidelines
- **Security**: Report security issues privately via [GitHub security advisories](https://github.com/danielscholl-osdu/osdu-fork-template/security/advisories)

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

---

**Need help?** Check the [documentation](https://danielscholl-osdu.github.io/osdu-fork-template/) or [open an issue](https://github.com/danielscholl-osdu/osdu-fork-template/issues/new).