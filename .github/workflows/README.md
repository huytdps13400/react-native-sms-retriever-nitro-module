# GitHub Actions Workflows

## Setup

### Required Secrets

Bạn cần thêm secrets sau vào GitHub repository:

1. **NPM_TOKEN**: Token để publish lên npm
   - Vào https://www.npmjs.com/settings/[username]/tokens
   - Tạo "Classic Token" với quyền "Automation"
   - Copy token và thêm vào GitHub: Settings → Secrets and variables → Actions → New repository secret
   - Name: `NPM_TOKEN`

## Workflows

### 1. CI (`ci.yml`)
Chạy tự động khi:
- Push lên branch `main`
- Tạo Pull Request

Jobs:
- Lint code
- Type check
- Run tests
- Build library
- Build Android example

### 2. Auto Publish (`publish.yml`)
Tự động publish lên npm khi push code lên `main` branch.

**Cách hoạt động:**
- Chạy CI checks trước
- Tự động tăng version (patch)
- Build package
- Publish lên npm
- Tạo GitHub Release với tag

**Skip publish:**
Thêm `[skip publish]` hoặc `[skip ci]` vào commit message:
```bash
git commit -m "docs: update README [skip publish]"
```

**Ignore paths:**
- Thay đổi file `.md`
- Thay đổi trong `example/`
- Thay đổi trong `.github/` (trừ workflow files)

### 3. Manual Publish (`publish-manual.yml`)
Manual trigger để publish với version bump tùy chọn.

**Cách sử dụng:**
1. Vào GitHub repository
2. Actions tab → "Manual Publish to NPM"
3. Click "Run workflow"
4. Chọn version bump type:
   - `patch`: 1.0.0 → 1.0.1
   - `minor`: 1.0.0 → 1.1.0
   - `major`: 1.0.0 → 2.0.0
5. Click "Run workflow"

## Notes

- Tất cả workflows đều sử dụng Node.js 20
- Auto publish chỉ bump `patch` version
- Manual publish cho phép chọn version bump type
- Commits từ workflow có tag `[skip ci]` để tránh loop
- Git commits được tạo bởi `github-actions[bot]`

## First Time Setup

```bash
# 1. Tạo NPM token
npm login
npm token create

# 2. Thêm NPM_TOKEN vào GitHub Secrets

# 3. Push code lên main
git add .
git commit -m "chore: setup CI/CD workflows"
git push origin main

# 4. Workflow sẽ tự động chạy và publish
```

## Troubleshooting

### Publish failed với "403 Forbidden"
- Check NPM_TOKEN có đúng không
- Token cần quyền "Automation"
- Package name phải match với npm account

### Git push failed
- Check GitHub token permissions
- Repository settings → Actions → General → Workflow permissions
- Chọn "Read and write permissions"

### CI không chạy
- Check branch name (phải là `main`)
- Check `.github/workflows/*.yml` syntax
