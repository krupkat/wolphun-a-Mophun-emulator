package com.manedsoftware.wolphun;

import java.io.File;
import java.util.Arrays;
import java.util.Random;

/** Dependency-free regression tests; optional arguments exercise local ROMs without modifying them. */
public final class MophunCryptTest {
    private static int checks;

    private static void check(boolean condition, String message){
        checks++;
        if(!condition) throw new AssertionError(message);
    }

    private static void put32(byte[] d, int offset, int value){
        for(int i=0;i<4;i++) d[offset+i]=(byte)(value >>> (8*i));
    }

    private static int get32(byte[] d, int offset){
        int value=0;
        for(int i=0;i<4;i++) value|=(d[offset+i]&255) << (8*i);
        return value;
    }

    private static byte[] highVarietyPlaintext(){
        byte[] d=new byte[40+4096+4];
        d[0]='V'; d[1]='M'; d[2]='G'; d[3]='P';
        put32(d,12,4096);
        put32(d,24,4); // empty, zero-terminated resource table
        for(int n=0;n<512;n++){
            int pc=40+n*8;
            d[pc]=0x5a; // LDI, followed by a raw immediate (NOT another instruction)
            put32(d,pc+4,0x80000000 | n*0x010203);
        }
        return d;
    }

    private static byte[] prepare(byte[] d){
        if(MophunCrypt.looksEncrypted(d)){
            byte[] decrypted=MophunCrypt.decrypt(d);
            if(decrypted!=null) return decrypted;
        }
        return d;
    }

    public static void main(String[] args) throws Exception {
        byte[] plain=highVarietyPlaintext();
        boolean[] seen=new boolean[256]; int distinct=0;
        for(int n=40;n<4136;n++) if(!seen[plain[n]&255]){seen[plain[n]&255]=true; distinct++;}
        check(distinct>190,"Fixture must reproduce the old high-byte-variety false positive");
        check(!MophunCrypt.looksEncrypted(plain),"High-variety PIP2 code is not encrypted");
        check(prepare(plain)==plain,"Import leaves plaintext untouched");
        check(prepare(prepare(plain))==plain,"Launch does not decrypt imported plaintext again");

        byte[] encrypted=plain.clone(); byte[] noise=new byte[4096];
        new Random(42).nextBytes(noise); System.arraycopy(noise,0,encrypted,40,noise.length);
        encrypted[40]=(byte)0xe7;
        check(MophunCrypt.looksEncrypted(encrypted),"High-variety invalid bytecode still looks encrypted");
        check(prepare(encrypted)==encrypted,"Missing META/key leaves the input untouched");

        byte[] badPool=plain.clone(); put32(badPool,44,123456);
        check(MophunCrypt.looksEncrypted(badPool),"Out-of-range pool references do not validate entry code");
        byte[] brokenPrefix=plain.clone(); brokenPrefix[40+20*8]=(byte)0xe7;
        check(MophunCrypt.looksEncrypted(brokenPrefix),"Validate a sustained prefix, not just the first opcode");
        byte[] mixed=plain.clone();
        mixed[40]=0x40; mixed[41]=0; mixed[42]=1; mixed[43]=0; // LDQ, four bytes
        mixed[44]=0x01; mixed[45]=0; mixed[46]=0; mixed[47]=0; // NOP, four bytes
        check(!MophunCrypt.looksEncrypted(mixed),"Recognize mixed four/eight-byte instructions");

        byte[] invalid=plain.clone(); invalid[0]='X';
        check(!MophunCrypt.looksEncrypted(invalid),"Ignore non-VMGP input");
        check(!MophunCrypt.looksEncrypted(new byte[12]),"Ignore incomplete headers");
        check(!MophunCrypt.looksEncrypted(Arrays.copyOf(plain,100)),"Ignore truncated code sections");
        invalid=plain.clone(); put32(invalid,12,-1);
        check(!MophunCrypt.looksEncrypted(invalid),"Reject negative code lengths safely");
        invalid=plain.clone(); put32(invalid,12,Integer.MAX_VALUE);
        check(!MophunCrypt.looksEncrypted(invalid),"Reject overflowing code lengths safely");

        int decryptedRoms=0;
        for(String path: args){
            File file=new File(path);
            byte[] original=MophunCrypt.readAll(file), unchanged=original.clone();
            byte[] imported=prepare(original);
            byte[] launched=prepare(imported);
            check(Arrays.equals(original,unchanged),"Never modify the input: "+path);
            check(Arrays.equals(imported,launched),"Import/launch decryption is idempotent: "+path);
            if(imported!=original){
                decryptedRoms++;
                check(!MophunCrypt.looksEncrypted(imported),"Recognize decrypted bytecode: "+path);
                int codeEnd=40+get32(original,12);
                check(Arrays.equals(Arrays.copyOf(original,40),Arrays.copyOf(imported,40)),
                    "Preserve VMGP header: "+path);
                check(Arrays.equals(Arrays.copyOfRange(original,codeEnd,original.length),
                                    Arrays.copyOfRange(imported,codeEnd,imported.length)),
                    "Preserve resources, pool, strings and data: "+path);
            }
        }
        System.out.println("PASS: "+checks+" crypt checks; "+args.length+" local ROMs, "+decryptedRoms+" decrypted once.");
    }
}
