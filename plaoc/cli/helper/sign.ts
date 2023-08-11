
import { createSign, createVerify } from "../deps.ts";

// 生成签名的函数
export function sign(algorithm: string, message: string, privateKey: string): string {

  const sign = createSign(algorithm);
  sign.update(message);
  
  const signature = sign.sign(privateKey)
  
  return `hex:${signature.toString()}`;

}

// 验证签名的函数
export function verify(algorithm: string, message: string, publicKey: string, signature: string): boolean {

  const verify = createVerify(algorithm);
  verify.update(message);

  const isValid = verify.verify(publicKey, signature.slice(4));

  return isValid;

}
