package fu.de191056.reservation.repository;

import fu.de191056.reservation.dto.ApiResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Remote data source for rooms: RoomService called via OpenFeign. */
@FeignClient(name = "room-service", url = "${room.service.url}")
public interface RoomClient {

    @GetMapping("/api/rooms/{roomId}")
    ApiResponseDTO getRoomById(@PathVariable("roomId") Long roomId);
}
