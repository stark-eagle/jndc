# J NDC✈️✈️✈️
![jdk12](https://img.shields.io/badge/jdk-12-orange.svg) 

## 介绍
"j ndc" 是 "java no distance connection"的缩写，意在提供简便易用的端口映射应用，应用基于netty编写。 


## ndc私有协议
* 协议设计为仅支持ipv4
* 单包数据长度限制,超出将自动拆包
```
public static final int AUTO_UNPACK_LENGTH = 5 * 1024 * 1024
```
* 协议结构：
```
--------------------------------
  3byte      2byte      1byte
|  ndc   |  version  |  type   |
--------------------------------
            4byte
|          local ip            |
--------------------------------
            4byte
|          remote ip           |
--------------------------------
            4byte
|          local port          |
--------------------------------
            4byte
|          server port         |
--------------------------------
            4byte
|          remote port         |
--------------------------------
            7byte
|          data length         |
--------------------------------
           data length byte
|            data              |
--------------------------------
```

## 开发计划

