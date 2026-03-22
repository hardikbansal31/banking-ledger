package com.bankingcore.bankingledger.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * QuartzConfig — wires Spring's ApplicationContext into Quartz's job factory.
 *
 * THE PROBLEM THIS SOLVES:
 *   By default, Quartz instantiates Job objects using plain Java reflection
 *   (new TransferJob()). Spring knows nothing about these instances — they
 *   have no beans injected, no @Autowired fields populated.
 *
 * THE FIX — SpringBeanJobFactory:
 *   This custom factory creates Job instances through Spring's
 *   AutowireCapableBeanFactory. Spring sees the Job as a bean, injects
 *   all @Autowired dependencies (LedgerService, DistributedLockService etc.)
 *   and manages its lifecycle.
 *
 * WITHOUT THIS:
 *   TransferJob.execute() would throw NullPointerException on the first
 *   ledgerService.transfer() call because ledgerService was never injected.
 */
@Configuration
public class QuartzConfig {

    @Bean
    public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
        factory.setApplicationContext(applicationContext);
        return factory;
    }

    /**
     * SpringBeanJobFactory extension that autowires Job instances via Spring.
     */
    static class AutowiringSpringBeanJobFactory
            extends SpringBeanJobFactory
            implements ApplicationContextAware {

        private AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(@NonNull ApplicationContext context) {
            this.beanFactory = context.getAutowireCapableBeanFactory();
        }

        @Override
        @NonNull
        protected Object createJobInstance(@NonNull TriggerFiredBundle bundle)
                throws Exception {
            Object job = super.createJobInstance(bundle);
            // This is the key call — it injects @Autowired fields into the job
            beanFactory.autowireBean(job);
            return job;
        }
    }
}