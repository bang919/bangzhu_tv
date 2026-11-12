import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

public class SignManifest {
    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("keystore password-file manifest signature");
        char[] password = Files.readString(Path.of(args[1])).trim().toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of(args[0]))) { store.load(input, password); }
        PrivateKey key = (PrivateKey) store.getKey("interface-update", password);
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key);
        signature.update(Files.readAllBytes(Path.of(args[2])));
        Files.write(Path.of(args[3]), signature.sign());
        java.util.Arrays.fill(password, '\0');
    }
}
