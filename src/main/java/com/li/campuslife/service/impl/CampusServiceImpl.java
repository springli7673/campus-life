package com.li.campuslife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.entity.Campus;
import com.li.campuslife.mapper.CampusMapper;
import com.li.campuslife.service.ICampusService;
import org.springframework.stereotype.Service;

@Service
public class CampusServiceImpl extends ServiceImpl<CampusMapper, Campus> implements ICampusService {
}
