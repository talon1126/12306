package com.talon.index12306.ticketservice.mapstruct;

import com.talon.index12306.ticketservice.dao.entity.RegionDO;
import com.talon.index12306.ticketservice.dao.entity.StationDO;
import com.talon.index12306.ticketservice.dao.entity.TrainStationDO;
import com.talon.index12306.ticketservice.dto.resp.RegionStationQueryRespDTO;
import com.talon.index12306.ticketservice.dto.resp.StationQueryRespDTO;
import com.talon.index12306.ticketservice.dto.resp.TrainStationQueryRespDTO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TicketConverter {

    List<RegionStationQueryRespDTO> stationDOs2RSQueryRespDTOs(List<StationDO> stationDOS);

    List<StationQueryRespDTO> stationDOs2SQueryRespDTOs(List<StationDO> stationDOS);

    List<RegionStationQueryRespDTO> RegionDOs2RSQueryRespDTOs(List<RegionDO> stationDOS);

    List<TrainStationQueryRespDTO> TrainStationDOs2RespDTOs(List<TrainStationDO> trainStationDOs);

}
