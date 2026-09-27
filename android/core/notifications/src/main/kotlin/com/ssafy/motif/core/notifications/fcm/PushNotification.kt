package com.ssafy.motif.core.notifications.fcm

enum class PushNotificationType {
    NEW_CHALLENGE,
    NEW_NOTICE,
    NEW_COMMENT,
    NEW_REPLY,
    COMPOSITE_DONE,
    COMPOSITE_FAILED,
    ;

    companion object {

        fun fromServerValue(
            value: String?,
        ): PushNotificationType? =
            entries.firstOrNull {
                it.name.equals(
                    other = value,
                    ignoreCase = true,
                )
            }
    }
}

enum class CompositeJobType {
    MOTIF,
    CHALLENGE,
    ;

    companion object {

        fun fromServerValue(
            value: String?,
        ): CompositeJobType? =
            entries.firstOrNull {
                it.name.equals(
                    other = value,
                    ignoreCase = true,
                )
            }
    }
}

data class PushNotification(
    val type: PushNotificationType,
    val title: String,
    val body: String,
    val sentDttm: String?,
    val challengeId: Long? = null,
    val videoId: Long? = null,
    val noticeId: Long? = null,
    val commentId: Long? = null,
    val jobUuid: String? = null,
    val jobType: CompositeJobType? = null,
    val failCode: String? = null,
)

internal fun Map<String, String>.toPushNotification():
        PushNotification? {

    val type =
        PushNotificationType.fromServerValue(
            this[KEY_TYPE],
        )
            ?: return null

    val jobUuid =
        this[KEY_JOB_UUID]
            ?.takeIf(String::isNotBlank)

    val jobType =
        CompositeJobType.fromServerValue(
            this[KEY_JOB_TYPE],
        )

    if (
        type == PushNotificationType.COMPOSITE_DONE ||
        type == PushNotificationType.COMPOSITE_FAILED
    ) {
        if (
            jobUuid == null ||
            jobType == null
        ) {
            return null
        }
    }

    return PushNotification(
        type = type,
        title =
            this[KEY_TITLE]
                .orEmpty(),
        body =
            this[KEY_BODY]
                .orEmpty(),
        sentDttm =
            this[KEY_SENT_DTTM]
                ?.takeIf(String::isNotBlank),
        challengeId =
            this[KEY_CHALLENGE_SEQ]
                ?.toLongOrNull(),
        videoId =
            this[KEY_VIDEO_SEQ]
                ?.toLongOrNull(),
        noticeId =
            this[KEY_NOTICE_SEQ]
                ?.toLongOrNull(),
        commentId =
            this[KEY_COMMENT_SEQ]
                ?.toLongOrNull(),
        jobUuid =
            jobUuid,
        jobType =
            jobType,
        failCode =
            this[KEY_FAIL_CODE]
                ?.takeIf(String::isNotBlank),
    )
}

private const val KEY_TYPE = "type"
private const val KEY_TITLE = "title"
private const val KEY_BODY = "body"
private const val KEY_SENT_DTTM = "sentDttm"

private const val KEY_CHALLENGE_SEQ = "challengeSeq"
private const val KEY_VIDEO_SEQ = "videoSeq"
private const val KEY_NOTICE_SEQ = "noticeSeq"
private const val KEY_COMMENT_SEQ = "commentSeq"

private const val KEY_JOB_UUID = "jobUuid"
private const val KEY_JOB_TYPE = "jobType"
private const val KEY_FAIL_CODE = "failCode"