import java.math.BigDecimal;
import java.math.RoundingMode;

public class Test {
    public static void main(String[] args) {
        BigDecimal bd = new BigDecimal("94.0000000000000000");
        System.out.println(bd.setScale(0, RoundingMode.DOWN).toPlainString());
    }
}