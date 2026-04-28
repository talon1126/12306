package com.talon.index12306.userservice.mapstruct;

import com.talon.index12306.userservice.dto.req.PassengerReqDTO;
import com.talon.index12306.userservice.dto.resp.PassengerActualRespDTO;
import com.talon.index12306.userservice.dto.resp.PassengerRespDTO;
import com.talon.index12306.userservice.dao.entity.Passenger;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PassengerConverter {

    PassengerRespDTO Passenger2RespDTO(Passenger passenger);

    List<PassengerRespDTO> Passengers2RespDTOs(List<Passenger> passengers);

    PassengerActualRespDTO Passenger2ActualRespDTO (Passenger passenger);

    List<PassengerActualRespDTO> Passengers2ActualRespDTOs (List<Passenger> passengers);

    Passenger ReqDTO2Passenger(PassengerReqDTO passengerReqDTO);
}
