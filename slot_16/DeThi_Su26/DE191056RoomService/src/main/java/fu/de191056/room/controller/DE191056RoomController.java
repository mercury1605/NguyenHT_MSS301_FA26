package fu.de191056.room.controller;

import fu.de191056.room.common.OnCreate;
import fu.de191056.room.dto.ApiResponseDTO;
import fu.de191056.room.dto.RoomDTO;
import fu.de191056.room.entity.RoomStatus;
import fu.de191056.room.entity.RoomType;
import fu.de191056.room.service.RoomService;
import jakarta.validation.groups.Default;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class DE191056RoomController {
    private final RoomService roomService;

    public DE191056RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<ApiResponseDTO> create(@Validated({Default.class, OnCreate.class}) @RequestBody RoomDTO roomDTO,
                                                 BindingResult br) {
        if (br.hasErrors()) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                    .body(ApiResponseDTO.error(2, firstError(br)));
        }
        return map(roomService.createRoom(roomDTO), HttpStatus.CREATED);
    }

    @PutMapping("/{roomId}")
    public ResponseEntity<ApiResponseDTO> update(@PathVariable Long roomId,
                                                 @Validated @RequestBody RoomDTO roomDTO,
                                                 BindingResult br) {
        if (br.hasErrors()) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                    .body(ApiResponseDTO.error(2, firstError(br)));
        }
        return map(roomService.updateRoom(roomId, roomDTO), HttpStatus.OK);
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponseDTO> getDetail(@PathVariable Long roomId) {
        return map(roomService.getRoomById(roomId), HttpStatus.OK);
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponseDTO> setMaintenance(@PathVariable Long roomId) {
        return map(roomService.setRoomMaintenance(roomId), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<ApiResponseDTO> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size,
                                               @RequestParam(required = false) RoomType roomType,
                                               @RequestParam(required = false) RoomStatus status) {
        if (page < 0 || size < 1 || size > 100) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                    .body(ApiResponseDTO.error(2, "page must be >= 0 and size must be between 1 and 100"));
        }
        return map(roomService.getRoomList(roomType, status, PageRequest.of(page, size)), HttpStatus.OK);
    }

    private String firstError(BindingResult br) {
        return br.getAllErrors().isEmpty() ? "Validation failed" : br.getAllErrors().get(0).getDefaultMessage();
    }

    private ResponseEntity<ApiResponseDTO> map(ApiResponseDTO res, HttpStatus okStatus) {
        HttpStatus httpStatus = switch (res.getStatus()) {
            case 1 -> okStatus;
            case 2 -> HttpStatus.NOT_ACCEPTABLE;
            case 3 -> HttpStatus.IM_USED;
            case 4 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(httpStatus).body(res);
    }
}
