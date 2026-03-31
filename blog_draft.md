[Fullstack] Guava BloomFilter로 초고성능 토큰 블랙리스트 구축하기 - Redis 부하 99% 줄이기

JWT(JSON Web Token)는 상태가 없다는 게 장점이지만, 로그아웃이나 계정 탈취 대응을 위한 '토큰 무효화(Revocation)' 기능이 들어가면 결국 서버 어딘가에 상태를 저장해야 한다. 보통 Redis에 블랙리스트를 두고 요청마다 확인하는 방식을 쓰는데, 트래픽이 몰리면 이 간단한 확인 작업이 Redis에 엄청난 I/O 부하를 준다.

모든 요청에서 '아직 유효한 토큰'인데도 불구하고 Redis에 물어보는 건 자원 낭비다. 이 문제를 해결하기 위해 Guava의 BloomFilter를 도입해 2단계 검증 시스템을 구축했다.

1단계: 로컬 메모리의 BloomFilter (확률적 필터)
BloomFilter는 "이 토큰이 블랙리스트에 확실히 없는가?"를 0%의 오차로 대답해준다. 만약 필터가 "없다"고 하면 Redis를 쳐다보지도 않고 즉시 통과시킨다. 반대로 "있을 수도 있다"고 하면 그때만 Redis를 조회한다. 약 1%의 오탐률(False Positive)을 허용해도 Redis 조회 횟수를 99% 이상 줄일 수 있다.

핵심 구현 코드다. BloomFilterTokenBlacklistService.kt의 일부다.

@Service
class BloomFilterTokenBlacklistService(
    private val redisTemplate: StringRedisTemplate
) : TokenBlacklistService {

    private val bloomFilter: BloomFilter<String> = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        100_000, // 10만 건까지 1% 오탐률 유지
        0.01
    )

    override fun isBlacklisted(token: String): Boolean {
        // 1단계: 메모리 레벨에서 컷 (초고속)
        val mightBePresent = synchronized(bloomFilter) { bloomFilter.mightContain(token) }
        if (!mightBePresent) return false

        // 2단계: 실제로 블랙리스트인지 Redis에서 최종 확인
        return redisTemplate.hasKey(redisKey(token)) == true
    }
    
    // ... 생략 ...
}

JwtAuthenticationFilter는 매 요청마다 이 서비스를 거친다. 필터 한 줄로 모든 API 보안이 강화된다.

if (token != null) {
    if (tokenBlacklistService.isBlacklisted(token)) {
        log.warn("Rejected blacklisted JWT at {}", requestUri)
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token has been revoked")
        return
    }
    // ... 토큰 유효성 검증 로직 ...
}

성능상 이점이 명확하다. 대부분의 정상적인 요청은 JVM 내부 메모리 연산만으로 끝나기 때문에 네트워크 지연(Network Latency)이 0에 가깝다. 로그아웃이 발생할 때만 Redis와 BloomFilter를 동시에 업데이트하면 된다.

물론 분산 환경(다중 인스턴스)에서는 각 인스턴스마다 로컬 BloomFilter를 가지고 있기 때문에, 로그아웃 직후 다른 인스턴스로 들어오는 요청에 대해 아주 짧은 시간(수 밀리초) 동안은 Redis만 조회하거나 약간의 오탐이 있을 수 있다. 하지만 보안 결함은 아니며(오히려 더 꼼꼼히 체크하게 됨), 성능 최적화라는 목적에는 완벽히 부합한다.

이번 구현으로 시스템 전체의 응답 속도는 유지하면서도 보안 레벨을 한 단계 더 끌어올렸다. 단순한 Redis 조회보다 훨씬 세련된 엔지니어링 접근법이라 생각한다.
