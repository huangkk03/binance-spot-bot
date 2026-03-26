package com.binance.compound.service;

import com.binance.compound.entity.SimAccount;
import com.binance.compound.repository.SimAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimAccountService {
    
    private final SimAccountRepository simAccountRepository;
    
    @Transactional
    public void deposit(String asset, BigDecimal amount, Boolean isSimulation) {
        SimAccount account = simAccountRepository.findByAssetAndIsSimulation(asset.toUpperCase(), isSimulation)
                .orElseGet(() -> SimAccount.builder()
                        .asset(asset.toUpperCase())
                        .freeBalance(BigDecimal.ZERO)
                        .lockedBalance(BigDecimal.ZERO)
                        .isSimulation(isSimulation)
                        .build());
        
        account.setFreeBalance(account.getFreeBalance().add(amount));
        simAccountRepository.save(account);
        
        log.info("[SIM] Deposited {} {} to simulation account. New balance: {}", 
                amount, asset, account.getFreeBalance());
    }
    
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String asset, Boolean isSimulation) {
        return simAccountRepository.findByAssetAndIsSimulation(asset.toUpperCase(), isSimulation)
                .map(SimAccount::getFreeBalance)
                .orElse(BigDecimal.ZERO);
    }
    
    @Transactional
    public void setBalance(String asset, BigDecimal amount, Boolean isSimulation) {
        SimAccount account = simAccountRepository.findByAssetAndIsSimulation(asset.toUpperCase(), isSimulation)
                .orElseGet(() -> SimAccount.builder()
                        .asset(asset.toUpperCase())
                        .lockedBalance(BigDecimal.ZERO)
                        .isSimulation(isSimulation)
                        .build());
        
        account.setFreeBalance(amount);
        simAccountRepository.save(account);
    }
    
    @Transactional(readOnly = true)
    public SimAccount getAccount(String asset, Boolean isSimulation) {
        return simAccountRepository.findByAssetAndIsSimulation(asset.toUpperCase(), isSimulation)
                .orElse(null);
    }
}
