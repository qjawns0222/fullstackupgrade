# Mission: Secure & Fix JWT Reissue Logic (RTR Strategy)

## 1. Problem Definition (Analysis)
### Current Status
- **Critical Logic Bug**: `AuthService.reissue()` calls `jwtTokenProvider.getAuthentication(refreshToken)`. The `getAuthentication` method throws an exception if the `auth` claim is missing. However, `createToken` **does not include** the `auth` claim in the generated refresh token. This causes the reissue process to fail with "권한 정보가 없는 토큰입니다.".
- **Security Vulnerability**: The current implementation attempts to rotate tokens by deleting the old one and saving a new one, but because of the crash, it never completes. Also, relying solely on the JWT signature for the refresh token without verifying its existence/state in Redis (before parsing claims) can be improved, though the current check `refreshTokenRepository.findById` is present.
- **Architecture**: The `getAuthentication` method abuses the JWT claims to reconstruct the `Authentication` object, which is fine for Access Tokens but fails for Refresh Tokens that are designed to be lightweight.

## 2. Solution Strategy (Technical Approach)
### 1. Separate Validation Logic
- **Refactor `JwtTokenProvider`**:
    - Create `getAuthentication(accessToken)` for Access Tokens (requires `auth` claim).
    - Create a new method `getSubject(token)` or similar to extract just the username from the Refresh Token without requiring `auth` claims.
    - Or modify `getAuthentication` to be more flexible, but separation is cleaner.

### 2. Implement Proper Refresh Token Rotation (RTR)
- **Refactor `AuthService.reissue`**:
    - **Step 1**: Validate Refresh Token signature.
    - **Step 2**: Retrieve `RefreshToken` entity from Redis using the token string.
    - **Step 3**: Verify ownership (`username` match).
    - **Step 4**: Load fresh user details (Authorities) from the database (via `CustomUserDetailsService` or similar) instead of trusting the old token's claims. *This is more secure as it reflects role changes immediately.*
    - **Step 5**: Generate new Access Token & **New Refresh Token**.
    - **Step 6**: Delete old Refresh Token, Save new Refresh Token (Rotation).
    - **Atomic Operation**: Ensure the delete/save is handled correctly (Redis is atomic enough for this per key).

### 3. Testing
- Create a test case that reproduces the `reissue` failure.
- Verify that `reissue` returns a *new* Refresh Token.
- Verify that the old Refresh Token is invalidated (removed from Redis).

## 3. Implementation Plan
1.  **Test**: Create `AuthServiceTest` (or `AuthIntegrationTest`) to reproduce the bug.
2.  **Refactor**: Modify `JwtTokenProvider` to extract Main Subject independently of `auth` claims.
3.  **Refactor**: Modify `AuthService` to load `Authentication` from `UserDetailsService` (need to check if `CustomUserDetailsService` exists, if not, implement or use `MemberRepository` directly).
    - *Check*: The project has `User` entity and `UserRepository`? (Saw `User.kt` in `find_by_name`).
4.  **Verify**: Run tests to ensure Reissue works and rotates tokens.
