package fu.de191056.postservice.entity;

/** Trang thai cua post. Luu vao DB duoi dang chuoi (nvarchar) qua @Enumerated(EnumType.STRING). */
public enum PostStatus {
    DRAFT, SCHEDULED, PUBLISHED, CANCELLED
}
