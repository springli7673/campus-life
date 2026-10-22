package com.li.campuslife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.li.campuslife.entity.ReconcileLog;
import com.li.campuslife.mapper.ReconcileLogMapper;
import com.li.campuslife.service.IReconcileLogService;
import org.springframework.stereotype.Service;

@Service
public class ReconcileLogServiceImpl extends ServiceImpl<ReconcileLogMapper, ReconcileLog>
        implements IReconcileLogService {
}
