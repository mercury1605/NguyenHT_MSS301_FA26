package fu.de191056.room.service;

import fu.de191056.room.dto.ApiResponseDTO;
import fu.de191056.room.dto.RoomDTO;
import fu.de191056.room.entity.RoomStatus;
import fu.de191056.room.entity.RoomType;
import org.springframework.data.domain.Pageable;

public interface RoomService {
    ApiResponseDTO createRoom(RoomDTO dto);
    ApiResponseDTO updateRoom(Long roomId, RoomDTO dto);
    ApiResponseDTO getRoomById(Long roomId);
    ApiResponseDTO setRoomMaintenance(Long roomId);
    ApiResponseDTO getRoomList(RoomType roomType, RoomStatus status, Pageable pageable);
}
