package com.ssafy.motif.feature.motif.impl.archive

import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.motif.SavedMotif

internal object ArchiveMockData {

    // TODO 보관함 전용 리소스로 교체. 현재는 공개 샘플 이미지·영상을 빌려 씀.
    // android.resource URL은 앱 패키지(com.ssafy.motif)에서만 해석되어 Run Preview(별도 test 패키지)에서는
    // 표시되지 않으므로, 영상과 같이 네트워크 이미지를 쓴다.
    private fun profileUrl(seed: String): String =
        "https://picsum.photos/seed/$seed/200"

    // 실기기에서 실제 재생을 확인할 수 있는 16:9 샘플 영상
    private val videoUrls = listOf(
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/kitten_fighting.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/dog.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/pencil_sketch.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/turtle.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_960,h_540,c_fill/elephants.mp4",
    )

    private val thumbnailUrls = listOf(
        "https://res.cloudinary.com/demo/video/upload/so_1,w_960,h_540,c_fill/kitten_fighting.jpg",
        "https://res.cloudinary.com/demo/video/upload/so_1,w_960,h_540,c_fill/dog.jpg",
        "https://res.cloudinary.com/demo/video/upload/so_1,w_960,h_540,c_fill/pencil_sketch.jpg",
        "https://res.cloudinary.com/demo/video/upload/so_1,w_960,h_540,c_fill/turtle.jpg",
        "https://res.cloudinary.com/demo/video/upload/so_1,w_960,h_540,c_fill/elephants.jpg",
    )

    val karina = Artist(id = 1L, name = "카리나", profileImageUrl = profileUrl("karina"), groupName = "aespa")
    val jisoo = Artist(id = 2L, name = "지수", profileImageUrl = profileUrl("jisoo"), groupName = "BLACKPINK")
    val chaeyoung = Artist(id = 3L, name = "이채영", profileImageUrl = profileUrl("chaeyoung"), groupName = "fromis_9")
    val haewon = Artist(id = 4L, name = "오해원", profileImageUrl = profileUrl("haewon"), groupName = "NMIXX")
    val yeji = Artist(id = 5L, name = "예지", profileImageUrl = profileUrl("yeji"), groupName = "ITZY")

    private val descriptions = listOf(
        "컴백 첫날이라 새 에셋으로 찍어봤다",
        "오늘 팬미팅 보고 와서 찍음",
        "카페에서 여유롭게 한 컷 ☕",
        "연습실 비하인드 느낌으로",
        "한강 노을이 너무 예뻐서 🌇",
        "생일 축하 영상으로 만든 거",
        "콘서트 다녀온 날 기념",
        "새로 나온 에셋 테스트해보기",
    )

    // 2026.08.31 00:00 KST
    private const val SAVED_BASE_TIME = 1_788_102_000_000L
    private const val ONE_DAY_MILLIS = 86_400_000L

    // index가 커질수록 savedAt이 작아지므로 목록 자체가 최근 저장순이다.
    // 18개 → 페이지 크기 6이면 6 / 6 / 6 으로 딱 나뉘고, hasNext는 세 번째 페이지에서 false가 된다.
    val savedMotifs: List<SavedMotif> = buildList {
        val artists = listOf(karina, jisoo, chaeyoung, haewon, yeji)

        repeat(18) { index ->
            add(
                SavedMotif(
                    id = index + 1L,
                    videoUrl = videoUrls[index % videoUrls.size],
                    thumbnailUrl = thumbnailUrls[index % thumbnailUrls.size],
                    description = descriptions[index % descriptions.size],
                    artist = artists[index % artists.size],
                    // 하루 이상 간격을 둬 날짜가 겹치지 않게 한다.
                    savedAt = SAVED_BASE_TIME - index * ONE_DAY_MILLIS * 3,
                    isRepresentative = index == 0,
                ),
            )
        }
    }
}
