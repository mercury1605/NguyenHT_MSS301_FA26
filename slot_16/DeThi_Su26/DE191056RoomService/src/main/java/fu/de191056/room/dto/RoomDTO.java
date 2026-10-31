package fu.de191056.room.dto;

import fu.de191056.room.common.OnCreate;
import fu.de191056.room.entity.RoomStatus;
import fu.de191056.room.entity.RoomType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Required checks (@NotNull/@NotBlank) belong to the OnCreate group so PUT can
 * send only the fields it wants to change; range/length checks always apply.
 */
public class RoomDTO {
    private Long roomId;

    @NotBlank(groups = OnCreate.class, message = "roomNumber is required")
    @Pattern(regexp = ".*\\S.*", message = "roomNumber must not be blank") // also on PUT; null passes
    @Size(max = 20, message = "roomNumber max length is 20")
    private String roomNumber;

    @NotNull(groups = OnCreate.class, message = "roomType is required")
    private RoomType roomType;

    @NotNull(groups = OnCreate.class, message = "pricePerNight is required")
    @DecimalMin(value = "0", inclusive = false, message = "pricePerNight must be > 0")
    @Digits(integer = 16, fraction = 2, message = "pricePerNight must fit Decimal(18,2)")
    private BigDecimal pricePerNight;

    @NotNull(groups = OnCreate.class, message = "capacity is required")
    @Min(value = 1, message = "capacity must be >= 1")
    @Max(value = 10, message = "capacity must be <= 10")
    private Integer capacity;

    @NotNull(groups = OnCreate.class, message = "floor is required")
    @Min(value = 1, message = "floor must be >= 1")
    private Integer floor;

    // Optional on create (defaults to AVAILABLE)
    private RoomStatus status;

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public RoomType getRoomType() { return roomType; }
    public void setRoomType(RoomType roomType) { this.roomType = roomType; }
    public BigDecimal getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(BigDecimal pricePerNight) { this.pricePerNight = pricePerNight; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getFloor() { return floor; }
    public void setFloor(Integer floor) { this.floor = floor; }
    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }
}
