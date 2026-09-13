package com.ssafy.motif.coreapi.admin.song;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.admin.song.dto.SongCreateRequest;
import com.ssafy.motif.coreapi.admin.song.dto.SongCreateResponse;
import com.ssafy.motif.coreapi.admin.song.dto.SongResponse;
import com.ssafy.motif.domain.core.song.Song;
import com.ssafy.motif.domain.core.song.SongRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 곡 관리 (어드민 명세 §3.6) — S15P21D104-389.
 *
 * <p><b>목록과 등록뿐이다.</b> 수정·삭제 API 는 명세에서 삭제됐다(R1) — 곡은 불변이고
 * 오류는 새 곡 등록 + 챌린지 재연결(§3.7.4)로 고친다. 이유는 {@link Song} javadoc 에 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSongService {

    /** §1.5 — {@code size} 기본 20 · 최대 100. */
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /**
     * 목록은 최신순 고정이다 — 방금 등록한 곡을 바로 찾는 화면이라 그 외의 정렬이 필요 없다.
     * {@code sort} 파라미터를 받지 않는 것은 §1.2 가 허용한 "서버 고정 정렬" 이다.
     * 받아 두면 화이트리스트가 필요하고, 없으면 오타 하나에 500 이 난다.
     */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "regDttm");

    private final SongRepository songRepository;
    private final AdminAuditLogger auditLogger;

    /** 목록 (§3.6.1). {@code q} 는 곡명·가수명 부분 일치. */
    @Transactional(readOnly = true)
    public PageResponse<SongResponse> list(String q, int page, int size) {
        Page<Song> found = songRepository.search(
                normalize(q), PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_SIZE), NEWEST_FIRST));

        Map<Integer, Long> challengeCounts = challengeCounts(found.getContent());
        List<SongResponse> list = found.getContent().stream()
                .map(song -> new SongResponse(song.getSongSeq(), song.getTitle(), song.getSingerName(),
                        challengeCounts.getOrDefault(song.getSongSeq(), 0L), song.getRegDttm()))
                .toList();

        // 페이지 계산은 Page 가 이미 한 값을 옮겨 담는다 — 여기서 다시 나누지 않는다
        return new PageResponse<>(list, found.getNumber(), found.getSize(), found.getTotalElements(),
                found.getTotalPages(), found.hasNext(), found.hasPrevious());
    }

    /**
     * 등록 (§3.6.2). 중복 곡명을 막지 않는다 — 리믹스·재녹음이 같은 제목을 쓴다.
     *
     * <p>{@code reg_usr_seq} 에 들어가는 값은 {@code admin.userSeq()} 다 —
     * {@code adminSeq} 가 아니다(§1.3 감사 컬럼 규약).
     */
    @Transactional
    public SongCreateResponse create(AuthenticatedAdmin admin, SongCreateRequest request) {
        Song song = songRepository.save(
                Song.register(request.title(), request.singerName(), admin.userSeq()));

        auditLogger.write(admin, "CREATE_SONG", "SONG", song.getSongSeq(), AdminAuditLogger.RESULT_OK);
        return new SongCreateResponse(song.getSongSeq());
    }

    /** 빈 페이지에 {@code in ()} 을 보내면 SQL 문법 오류다 — 물어볼 것이 없으면 묻지 않는다. */
    private Map<Integer, Long> challengeCounts(List<Song> songs) {
        if (songs.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Long> counts = new HashMap<>();
        songRepository.countChallenges(songs.stream().map(Song::getSongSeq).toList())
                .forEach(row -> counts.put(row.getSongSeq(), row.getChallengeCount()));
        return counts;
    }

    /**
     * {@code q} 없음·공백뿐 → 빈 문자열 = {@code '%%'} = 전체 조회.
     *
     * <p>{@code null} 을 그대로 넘기면 Postgres 가 타입 없는 파라미터를 {@code bytea} 로
     * 추론해 쿼리가 죽는다 — 자세한 사정은 {@code SongRepository.search} javadoc.
     */
    private static String normalize(String q) {
        return q == null ? "" : q.trim();
    }
}
