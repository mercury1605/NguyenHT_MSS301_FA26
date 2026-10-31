package fu.de191056.room.service.impl;

import fu.de191056.room.dto.ApiResponseDTO;
import fu.de191056.room.dto.PageDTO;
import fu.de191056.room.dto.RoomDTO;
import fu.de191056.room.entity.Room;
import fu.de191056.room.entity.RoomStatus;
import fu.de191056.room.entity.RoomType;
import fu.de191056.room.repository.RoomRepository;
import fu.de191056.room.service.RoomService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;

    public RoomServiceImpl(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    @Transactional
    public ApiResponseDTO createRoom(RoomDTO dto) {
        if (roomRepository.existsByRoomNumber(dto.getRoomNumber())) {
            return ApiResponseDTO.error(3, "Room number is duplicated");
        }
        Room room = new Room();
        room.setRoomNumber(dto.getRoomNumber());
        room.setRoomType(dto.getRoomType());
        room.setPricePerNight(dto.getPricePerNight());
        room.setCapacity(dto.getCapacity());
        room.setFloor(dto.getFloor());
        room.setStatus(dto.getStatus() == null ? RoomStatus.AVAILABLE : dto.getStatus());

        Room saved = roomRepository.save(room);
        return ApiResponseDTO.ok("Room created successfully", toDTO(saved));
    }

    @Override
    @Transactional
    public ApiResponseDTO updateRoom(Long roomId, RoomDTO dto) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ApiResponseDTO.error(4, "Room is not found");
        }
        if (dto.getRoomNumber() != null
                && roomRepository.existsByRoomNumberAndRoomIdNot(dto.getRoomNumber(), roomId)) {
            return ApiResponseDTO.error(3, "Room number is duplicated");
        }
        if (dto.getRoomNumber() != null)    room.setRoomNumber(dto.getRoomNumber());
        if (dto.getRoomType() != null)      room.setRoomType(dto.getRoomType());
        if (dto.getPricePerNight() != null) room.setPricePerNight(dto.getPricePerNight());
        if (dto.getCapacity() != null)      room.setCapacity(dto.getCapacity());
        if (dto.getFloor() != null)         room.setFloor(dto.getFloor());
        if (dto.getStatus() != null)        room.setStatus(dto.getStatus());

        Room saved = roomRepository.save(room);
        return ApiResponseDTO.ok("Room updated successfully", toDTO(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO getRoomById(Long roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ApiResponseDTO.error(4, "Room is not found");
        }
        return ApiResponseDTO.ok("Successful", toDTO(room));
    }

    @Override
    @Transactional
    public ApiResponseDTO setRoomMaintenance(Long roomId) {
        Room room = roomRepository.findById(roomId).orElse(null);
        if (room == null) {
            return ApiResponseDTO.error(4, "Room is not found");
        }
        room.setStatus(RoomStatus.MAINTENANCE);
        roomRepository.save(room);
        return ApiResponseDTO.ok("Room is set to MAINTENANCE successfully", null);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponseDTO getRoomList(RoomType roomType, RoomStatus status, Pageable pageable) {
        Page<Room> page = roomRepository.search(roomType, status, pageable);
        List<RoomDTO> content = page.getContent().stream().map(this::toDTO).toList();

        PageDTO pageDTO = new PageDTO(
                page.getSize(), page.getNumber(), page.getTotalPages(),
                page.getTotalElements(), page.isFirst(), page.isLast(), content);
        return ApiResponseDTO.ok("Successful", pageDTO);
    }

    private RoomDTO toDTO(Room r) {
        RoomDTO d = new RoomDTO();
        d.setRoomId(r.getRoomId());
        d.setRoomNumber(r.getRoomNumber());
        d.setRoomType(r.getRoomType());
        d.setPricePerNight(r.getPricePerNight());
        d.setCapacity(r.getCapacity());
        d.setFloor(r.getFloor());
        d.setStatus(r.getStatus());
        return d;
    }
}
