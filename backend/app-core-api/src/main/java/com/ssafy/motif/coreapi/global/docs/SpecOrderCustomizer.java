package com.ssafy.motif.coreapi.global.docs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 문서에 나오는 순서를 <b>API 명세의 절 순서</b>로 맞춘다 — 태그 묶음과 그 안의 오퍼레이션 둘 다.
 *
 * <p>springdoc 은 순서를 보장하지 않는다. 경로는 {@code RequestMappingHandlerMapping} 이 준
 * 순서 그대로이고 그 맵은 정렬돼 있지 않아, 핸들러를 하나 추가하거나 다시 빌드하는 것만으로
 * 화면이 뒤바뀐다. 안드로이드가 명세와 Swagger 를 나란히 놓고 읽는데 §2 다음이 §11 이면
 * 둘을 대조할 수 없다.
 *
 * <p><b>정렬 키는 오퍼레이션 {@code summary} 안의 절 번호다</b>({@code "숏폼 피드 (§7.1)"} →
 * {@code 7.1}). 별도의 순번 애너테이션을 두지 않는 이유는, 그런 값은 API 가 늘어날 때 조용히
 * 중복되거나 비고 <i>아무도 틀린 것을 알아채지 못하기</i> 때문이다. 절 번호는 이미 모든
 * summary 에 적혀 있고 명세가 바뀌면 같이 고쳐진다 — 새 API 는 summary 에 {@code (§x.y)} 만
 * 적으면 제자리에 놓인다. 숫자로 비교한다: 문자열로 하면 §5.10 이 §5.7 앞에 온다.
 *
 * <p>{@link #TAG_ORDER} 에 없는 태그(어드민 그룹)는 <b>알려진 태그 뒤</b>에 원래 태그 순서대로
 * 남고, 그 안에서는 경로 문자열 순이다. 절 번호로 다시 줄 세우지 않는다 — 어드민 명세는 절
 * 번호 체계가 따로라 섞으면 엉뚱한 자리에 놓인다.
 *
 * <p>같은 경로에 여러 메서드가 걸린 경우(예: {@code /videos/{videoSeq}} 의 GET §8.2 ·
 * PATCH §8.9 · DELETE §8.10)는 <b>가장 앞선 절</b>을 그 경로의 자리로 쓴다. OpenAPI 는 경로
 * 하나에 메서드를 묶으므로 쪼갤 수가 없고, 같은 리소스가 붙어 있는 편이 읽기도 낫다.
 *
 * <p><b>{@code deprecated = true} 인 경로는 제 태그의 맨 뒤로 간다.</b> 절 번호가 같은
 * 묶음(§1.7 미디어 3종)에서 전환 기간용 구 엔드포인트가 살아 있는 API 앞에 놓이면, 목록
 * 맨 위에 보이는 것이 쓰면 안 되는 쪽이 된다.
 *
 * <p>나머지 동률은 <b>경로 문자열</b>로 끊는다. 핸들러 등록 순서로 끊으면 그 순서 자체가
 * 빌드마다 달라져 고치려던 문제가 그대로 남는다.
 *
 * <p>{@code GlobalOpenApiCustomizer} 다 — 이유는 {@link ResponseSchemaCustomizer} 와 같다.
 */
@Component
public class SpecOrderCustomizer implements GlobalOpenApiCustomizer {

    /** 명세 절 순서. §1.7 미디어는 번호가 앞이지만 재생 공용 인프라라 끝에 둔다. */
    private static final List<String> TAG_ORDER = List.of(
            "인증 (AUTH)",             // §2
            "온보딩 (ONBOARDING)",     // §3
            "사용자 (USER)",           // §4 · §11.9
            "아티스트 (ARTIST)",       // §5 · §11.4
            "홈 (HOME)",               // §6
            "챌린지 원본 (CHALLENGE)",  // §7
            "참여 영상 (VIDEO)",       // §8
            "댓글 (COMMENT)",          // §9
            "공지 (NOTICE)",           // §10
            "모티프 (MOTIF)",          // §11
            "결제 (ORDER)",            // §12
            "합성 (COMPOSITE)",        // §13
            "알림 (NOTIFICATION)",     // §14
            "미디어 (MEDIA)");         // §1.7

    private static final Pattern SECTION = Pattern.compile("§(\\d+)\\.(\\d+)");

    /** 절 번호가 없으면 제 태그 안에서 맨 뒤로 — 빠뜨린 것이 눈에 띄게. */
    private static final int NO_SECTION = Integer.MAX_VALUE;

    @Override
    public void customise(OpenAPI openApi) {
        sortTags(openApi);
        sortPaths(openApi);
    }

    private void sortTags(OpenAPI openApi) {
        List<Tag> tags = openApi.getTags();
        if (tags == null || tags.isEmpty()) {
            return;
        }
        List<Tag> original = List.copyOf(tags);
        List<Tag> sorted = new ArrayList<>(tags);
        sorted.sort(Comparator.comparingInt((Tag tag) -> tagRank(tag.getName(), original.indexOf(tag))));
        openApi.setTags(sorted);
    }

    private void sortPaths(OpenAPI openApi) {
        Paths paths = openApi.getPaths();
        if (paths == null || paths.isEmpty()) {
            return;
        }
        List<String> tagNames = tagNames(openApi);
        List<Map.Entry<String, PathItem>> entries = new ArrayList<>(paths.entrySet());
        entries.sort(Comparator.comparing(entry -> keyOf(entry, tagNames)));

        Paths sorted = new Paths();
        sorted.setExtensions(paths.getExtensions());
        entries.forEach(entry -> sorted.addPathItem(entry.getKey(), entry.getValue()));
        openApi.setPaths(sorted);
    }

    /** 경로 하나의 자리 = 그 경로에 걸린 오퍼레이션 중 가장 앞선 (태그, 절). */
    private Key keyOf(Map.Entry<String, PathItem> entry, List<String> tagNames) {
        int bestTag = TAG_ORDER.size();
        int bestSection = NO_SECTION;
        boolean allDeprecated = true;
        for (Operation operation : entry.getValue().readOperations()) {
            int tag = tagRankOf(operation, tagNames);
            int section = tag < TAG_ORDER.size() ? sectionOf(operation.getSummary()) : NO_SECTION;
            if (tag < bestTag || (tag == bestTag && section < bestSection)) {
                bestTag = tag;
                bestSection = section;
            }
            allDeprecated &= Boolean.TRUE.equals(operation.getDeprecated());
        }
        return new Key(bestTag, allDeprecated, bestSection, entry.getKey());
    }

    private int tagRankOf(Operation operation, List<String> tagNames) {
        if (operation.getTags() == null) {
            return TAG_ORDER.size() + tagNames.size();
        }
        int best = Integer.MAX_VALUE;
        for (String name : operation.getTags()) {
            best = Math.min(best, tagRank(name, tagNames.indexOf(name)));
        }
        return best == Integer.MAX_VALUE ? TAG_ORDER.size() + tagNames.size() : best;
    }

    /** 아는 태그면 명세 순서, 모르면 그 뒤에 원래 태그 순서대로. */
    private int tagRank(String name, int fallbackIndex) {
        int known = TAG_ORDER.indexOf(name);
        return known >= 0 ? known : TAG_ORDER.size() + Math.max(fallbackIndex, 0);
    }

    /** {@code "숏폼 피드 (§7.1)"} → {@code 7_001}. 큰 수가 뒤로 간다. */
    private int sectionOf(String summary) {
        if (summary == null) {
            return NO_SECTION;
        }
        Matcher matcher = SECTION.matcher(summary);
        if (!matcher.find()) {
            return NO_SECTION;
        }
        return Integer.parseInt(matcher.group(1)) * 1000 + Integer.parseInt(matcher.group(2));
    }

    private List<String> tagNames(OpenAPI openApi) {
        if (openApi.getTags() == null) {
            return List.of();
        }
        return openApi.getTags().stream().map(Tag::getName).toList();
    }

    private record Key(int tagRank, boolean deprecated, int section, String path) implements Comparable<Key> {

        private static final Comparator<Key> ORDER = Comparator.comparingInt(Key::tagRank)
                .thenComparing(Key::deprecated)          // 살아 있는 API 가 먼저
                .thenComparingInt(Key::section)
                .thenComparing(Key::path);

        @Override
        public int compareTo(Key other) {
            return ORDER.compare(this, other);
        }
    }
}
