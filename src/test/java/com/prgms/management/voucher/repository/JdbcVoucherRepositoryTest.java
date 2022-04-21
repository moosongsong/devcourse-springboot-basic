package com.prgms.management.voucher.repository;

import com.prgms.management.voucher.entity.FixedAmountVoucher;
import com.prgms.management.voucher.entity.PercentDiscountVoucher;
import com.prgms.management.voucher.entity.Voucher;
import com.prgms.management.voucher.exception.InvalidVoucherParameterException;
import com.prgms.management.voucher.exception.VoucherDeleteFailException;
import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.ScriptResolver;
import com.wix.mysql.config.MysqldConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.wix.mysql.EmbeddedMysql.anEmbeddedMysql;
import static com.wix.mysql.config.Charset.UTF8;
import static com.wix.mysql.config.MysqldConfig.aMysqldConfig;
import static com.wix.mysql.distribution.Version.v5_7_latest;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringJUnitConfig
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
@ActiveProfiles("default")
@DisplayName("JdbcVoucherRepository 유닛 테스트")
class JdbcVoucherRepositoryTest {
  @Autowired
  VoucherRepository voucherRepository;
  
  @Autowired
  DataSource dataSource;
  
  EmbeddedMysql embeddedMysql;
  List<Voucher> vouchers = new ArrayList<>();
  
  @BeforeAll
  void setUp() {
    MysqldConfig config = aMysqldConfig(v5_7_latest)
      .withCharset(UTF8)
      .withPort(2215)
      .withUser("test", "1234")
      .withTimeZone("Asia/Seoul")
      .withTimeout(2, TimeUnit.MINUTES)
      .build();
    
    embeddedMysql = anEmbeddedMysql(config)
      .addSchema("demo", ScriptResolver.classPathScript("schema.sql"))
      .start();
  }
  
  @AfterAll
  void cleanUp() {
    embeddedMysql.stop();
  }
  
  @Configuration
  @ComponentScan
  static class Config {
    @Bean
    public DataSource dataSource() {
      return DataSourceBuilder.create()
        .url("jdbc:mysql://localhost:2215/demo")
        .username("test")
        .password("1234")
        .type(HikariDataSource.class)
        .build();
    }
    
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
      return new NamedParameterJdbcTemplate(dataSource);
    }
  }
  
  @DisplayName("save() : 바우처 저장 테스트")
  @Nested
  @Order(1)
  @TestMethodOrder(MethodOrderer.DisplayName.class)
  class SaveTest {
    @DisplayName("성공 : 0과 100 사이의 할인률이 지정된 경우 저장에 성공합니다.")
    @ParameterizedTest(name = "{0} 할인 바우처 저장 성공")
    @CsvSource({"1%, 1", "99%, 99"})
    void savePercentVoucherSuccess(String name, int percent) {
      Voucher newVoucher = new PercentDiscountVoucher(UUID.randomUUID(), name, percent,
        Timestamp.valueOf(LocalDateTime.now()));
      vouchers.add(voucherRepository.save(newVoucher));
      
      var retrievedVoucher = voucherRepository.findById(newVoucher.getVoucherId());
      assertThat(retrievedVoucher, not(nullValue()));
      assertThat(retrievedVoucher, equalTo(newVoucher));
    }
    
    @DisplayName("실패 : 0미만 100초과의 할인률이 지정된 경우 InvalidVoucherParameterException 예외가 발생합니다.")
    @ParameterizedTest(name = "{0} 할인 바우처 저장 실패")
    @CsvSource({"-1%, -1", "101%, 101"})
    void savePercentVoucherFail(String name, int percent) {
      assertThrows(InvalidVoucherParameterException.class, () -> {
        Voucher newVoucher = new PercentDiscountVoucher(UUID.randomUUID(), name, percent,
          Timestamp.valueOf(LocalDateTime.now()));
        vouchers.add(voucherRepository.save(newVoucher));
      });
    }
    
    @DisplayName("성공 : 0과 10000 사이의 금액이 지정된 경우 저장에 성공합니다.")
    @ParameterizedTest(name = "{0} 금액 바우처 저장 성공")
    @CsvSource({"10000원, 10000", "1원, 1"})
    void saveAmountVoucherSuccess(String name, int percent) {
      Voucher newVoucher = new FixedAmountVoucher(UUID.randomUUID(), name, percent,
        Timestamp.valueOf(LocalDateTime.now()));
      vouchers.add(voucherRepository.save(newVoucher));
      
      var retrievedVoucher = voucherRepository.findById(newVoucher.getVoucherId());
      assertThat(retrievedVoucher, not(nullValue()));
      assertThat(retrievedVoucher, equalTo(newVoucher));
    }
    
    @DisplayName("실패 : 0미만 10000초과의 금액이 지정된 경우 InvalidVoucherParameterException 예외가 발생합니다.")
    @ParameterizedTest(name = "{0} 금액 바우처 저장 실패")
    @CsvSource({"-1원, -1", "10001원, 10001"})
    void saveAmountVoucherFail(String name, int percent) {
      assertThrows(InvalidVoucherParameterException.class, () -> {
        Voucher newVoucher = new FixedAmountVoucher(UUID.randomUUID(), name, percent,
          Timestamp.valueOf(LocalDateTime.now()));
        vouchers.add(voucherRepository.save(newVoucher));
      });
    }
  }
  
  @DisplayName("findById() : ID로 바우처 조회 테스트")
  @Nested
  @Order(2)
  @TestMethodOrder(MethodOrderer.DisplayName.class)
  class FindByIdTest {
    @DisplayName("성공 : 존재하는 ID로 조회하는 경우 알맞은 바우처를 반환합니다.")
    @Test
    void findSuccess() {
      for (Voucher voucher : vouchers) {
        Voucher resultVoucher = voucherRepository.findById(voucher.getVoucherId());
        assertThat(voucher, equalTo(resultVoucher));
      }
    }
    
    @DisplayName("실패 : 존재하지 않는 ID로 조회하는 경우 null이 반환됩니다.")
    @Test
    void findFail() {
      assertThat(voucherRepository.findById(UUID.randomUUID()), is(nullValue()));
    }
  }
  
  @DisplayName("findAll() : 전체 바우처 목록 조회 테스트")
  @Nested
  @Order(3)
  @TestMethodOrder(MethodOrderer.DisplayName.class)
  class FindAllTest {
    @DisplayName("성공 : 한 개 이상의 바우처가 저장되어 있는 경우 전체 바우처 리스트가 반환됩니다.")
    @Test
    void findSuccess() {
      var retVoucher = voucherRepository.findAll();
      assertThat(retVoucher.isEmpty(), is(false));
      assertThat(retVoucher, hasSize(vouchers.size()));
    }
  }
  
  @DisplayName("removeById() : ID로 바우처 삭제 테스트")
  @Nested
  @Order(4)
  @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
  class RemoveByIdTest {
    List<UUID> oldVouchers = new ArrayList<>();
    
    @DisplayName("성공 : 존재하는 ID로 삭제하는 경우 삭제에 성공합니다.")
    @Test
    @Order(1)
    void removeSuccess() {
      int count = vouchers.size();
      
      for (Voucher voucher : vouchers) {
        oldVouchers.add(voucher.getVoucherId());
        voucherRepository.removeById(voucher.getVoucherId());
        
        var result = voucherRepository.findAll();
        assertThat(result, hasSize(--count));
      }
    }
    
    @DisplayName("실패 : 존재하지 않는 ID로 삭제하는 경우 VoucherDeleteFailException 예외가 발생합니다.")
    @Test
    @Order(2)
    void removeFail() {
      for (UUID id : oldVouchers) {
        assertThrows(VoucherDeleteFailException.class, () -> voucherRepository.removeById(id));
      }
    }
  }
}