package fu.de191056.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import fu.de191056.reservation.common.OnCreate;
import fu.de191056.reservation.entity.ReservationStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Date;

public class ReservationDTO {
    private Long reservationId;

    @NotBlank(groups = OnCreate.class, message = "guestName is required")
    @Pattern(regexp = ".*\\S.*", message = "guestName must not be blank")
    @Size(max = 100, message = "guestName max length is 100")
    private String guestName;

    @NotBlank(groups = OnCreate.class, message = "guestEmail is required")
    @Email(message = "guestEmail is invalid")
    @Size(max = 100, message = "guestEmail max length is 100")
    private String guestEmail;

    @NotBlank(groups = OnCreate.class, message = "guestPhone is required")
    @Pattern(regexp = ".*\\S.*", message = "guestPhone must not be blank")
    @Size(max = 20, message = "guestPhone max length is 20")
    private String guestPhone;

    @NotNull(groups = OnCreate.class, message = "checkInDate is required")
    @JsonFormat(pattern = "dd/MM/yyyy")
    private Date checkInDate;

    @NotNull(groups = OnCreate.class, message = "checkOutDate is required")
    @JsonFormat(pattern = "dd/MM/yyyy")
    private Date checkOutDate;

    @NotNull(groups = OnCreate.class, message = "numberOfGuests is required")
    @Min(value = 1, message = "numberOfGuests must be >= 1")
    @Max(value = 10, message = "numberOfGuests must be <= 10")
    private Integer numberOfGuests;

    private BigDecimal totalAmount;

    private ReservationStatus status;

    @NotNull(groups = OnCreate.class, message = "roomId is required")
    private Long roomId;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getGuestName() { return guestName; }
    public void setGuestName(String guestName) { this.guestName = guestName; }
    public String getGuestEmail() { return guestEmail; }
    public void setGuestEmail(String guestEmail) { this.guestEmail = guestEmail; }
    public String getGuestPhone() { return guestPhone; }
    public void setGuestPhone(String guestPhone) { this.guestPhone = guestPhone; }
    public Date getCheckInDate() { return checkInDate; }
    public void setCheckInDate(Date checkInDate) { this.checkInDate = checkInDate; }
    public Date getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(Date checkOutDate) { this.checkOutDate = checkOutDate; }
    public Integer getNumberOfGuests() { return numberOfGuests; }
    public void setNumberOfGuests(Integer numberOfGuests) { this.numberOfGuests = numberOfGuests; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
}
